package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RecordServiceTest {

    @Autowired
    private RecordService recordService;

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private UrlHasher urlHasher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(RecordCache.LAST_TOUCHED_LIVE_KEY);
    }

    private String uniqueUrl() {
        return "https://example.com/" + UUID.randomUUID();
    }

    @Test
    @DisplayName("adds and retrieves a record by shortcode")
    void addsAndRetrievesARecordByShortcode() {
        String url = uniqueUrl();

        Record added = recordService.addRecord(url);
        assertThat(added.shortcode()).hasSize(8);
        assertThat(added.url()).isEqualTo(url);

        Record retrieved = recordService.getRecord(added.shortcode());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.shortcode()).isEqualTo(added.shortcode());
        assertThat(retrieved.url()).isEqualTo(url);
        assertThat(retrieved.urlhash()).isEqualTo(added.urlhash());
    }

    @Test
    @DisplayName("retrieving an unknown shortcode returns null")
    void retrievingAnUnknownShortcodeReturnsNull() {
        assertThat(recordService.getRecord("00000000")).isNull();
    }

    @Test
    @DisplayName("adding the same URL twice returns the same shortcode")
    void addingTheSameUrlTwiceReturnsTheSameShortcode() {
        String url = uniqueUrl();

        Record first = recordService.addRecord(url);
        Record second = recordService.addRecord(url);

        assertThat(second.shortcode()).isEqualTo(first.shortcode());
    }

    @Test
    @DisplayName("resolveUrl returns the url for a freshly added record")
    void resolveUrlReturnsTheUrlForAFreshlyAddedRecord() {
        String url = uniqueUrl();
        Record added = recordService.addRecord(url);

        assertThat(recordService.resolveUrl(added.shortcode())).isEqualTo(url);
    }

    @Test
    @DisplayName("resolveUrl falls back to the database when the url isn't cached")
    void resolveUrlFallsBackToTheDatabaseWhenTheUrlIsNotCached() {
        String url = uniqueUrl();
        byte[] urlhash = urlHasher.hash(url);
        String shortcode = recordRepository.insertRecord(urlhash, url);

        assertThat(recordService.resolveUrl(shortcode)).isEqualTo(url);
    }

    @Test
    @DisplayName("resolveUrl returns null for an unknown shortcode")
    void resolveUrlReturnsNullForAnUnknownShortcode() {
        assertThat(recordService.resolveUrl("00000000")).isNull();
    }

    @Test
    @DisplayName("resolveUrl records the shortcode in the last-touched live bucket")
    void resolveUrlRecordsTheShortcodeInTheLastTouchedLiveBucket() {
        String url = uniqueUrl();
        Record added = recordService.addRecord(url);

        recordService.resolveUrl(added.shortcode());

        String touched = redisTemplate.<String, String>opsForHash()
            .get(RecordCache.LAST_TOUCHED_LIVE_KEY, added.shortcode());
        assertThat(touched).isNotNull();
    }

    @Test
    @DisplayName("resolveUrl does not touch the last-touched bucket for an unknown shortcode")
    void resolveUrlDoesNotTouchTheLastTouchedBucketForAnUnknownShortcode() {
        recordService.resolveUrl("00000000");

        String touched = redisTemplate.<String, String>opsForHash()
            .get(RecordCache.LAST_TOUCHED_LIVE_KEY, "00000000");
        assertThat(touched).isNull();
    }
}
