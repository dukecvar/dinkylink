package dev.dukecvar.dinkylink.api.record;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Component
public class RecordCache {
    private static final Duration TTL = Duration.ofDays(365);
    public static final String LAST_TOUCHED_LIVE_KEY = "last-touched:live";

    private final StringRedisTemplate redisTemplate;

    public RecordCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void touch(String shortcode) {
        redisTemplate.<String, String>opsForHash().put(LAST_TOUCHED_LIVE_KEY, shortcode, OffsetDateTime.now().toString());
    }

    public String getShortcode(byte[] urlhash) {
        return get(shortcodeKey(urlhash));
    }

    public void putShortcode(byte[] urlhash, String shortcode) {
        put(shortcodeKey(urlhash), shortcode);
    }

    public String getUrl(String shortcode) {
        return get(urlKey(shortcode));
    }

    public void putUrl(String shortcode, String url) {
        put(urlKey(shortcode), url);
    }

    private String get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.expire(key, TTL);
        }
        return value;
    }

    private void put(String key, String value) {
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    private String shortcodeKey(byte[] urlhash) {
        return "shortcodes:" + HexFormat.of().formatHex(urlhash);
    }

    private String urlKey(String shortcode) {
        return "urls:" + shortcode;
    }
}
