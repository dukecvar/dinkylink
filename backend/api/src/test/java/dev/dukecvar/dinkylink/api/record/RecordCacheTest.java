package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecordCacheTest {

    @Autowired
    private RecordCache recordCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> usedKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (!usedKeys.isEmpty()) {
            redisTemplate.delete(usedKeys);
        }
        usedKeys.clear();
    }

    private byte[] randomUrlhash() {
        return UUID.randomUUID().toString().getBytes();
    }

    @Test
    @DisplayName("stores and retrieves a shortcode by urlhash")
    void storesAndRetrievesAShortcodeByUrlhash() {
        byte[] urlhash = randomUrlhash();
        usedKeys.add("shortcodes:" + HexFormat.of().formatHex(urlhash));

        recordCache.putShortcode(urlhash, "abc12345");

        assertThat(recordCache.getShortcode(urlhash)).isEqualTo("abc12345");
    }

    @Test
    @DisplayName("returns null for an unknown urlhash")
    void returnsNullForAnUnknownUrlhash() {
        assertThat(recordCache.getShortcode(randomUrlhash())).isNull();
    }

    @Test
    @DisplayName("stores and retrieves a url by shortcode")
    void storesAndRetrievesAUrlByShortcode() {
        String shortcode = UUID.randomUUID().toString().substring(0, 8);
        usedKeys.add("urls:" + shortcode);

        recordCache.putUrl(shortcode, "https://example.com/foo");

        assertThat(recordCache.getUrl(shortcode)).isEqualTo("https://example.com/foo");
    }

    @Test
    @DisplayName("returns null for an unknown shortcode")
    void returnsNullForAnUnknownShortcode() {
        assertThat(recordCache.getUrl(UUID.randomUUID().toString().substring(0, 8))).isNull();
    }

    @Test
    @DisplayName("refreshes the url TTL on a cache hit")
    void refreshesTheUrlTtlOnACacheHit() {
        String shortcode = UUID.randomUUID().toString().substring(0, 8);
        String key = "urls:" + shortcode;
        usedKeys.add(key);

        recordCache.putUrl(shortcode, "https://example.com/foo");
        redisTemplate.expire(key, Duration.ofSeconds(5));

        recordCache.getUrl(shortcode);

        Long remaining = redisTemplate.getExpire(key, TimeUnit.DAYS);
        assertThat(remaining).as("expected TTL to be refreshed close to 365 days, was %d days", remaining).isGreaterThan(300);
    }
}
