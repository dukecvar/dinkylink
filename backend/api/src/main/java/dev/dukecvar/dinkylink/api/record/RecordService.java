package dev.dukecvar.dinkylink.api.record;

import org.springframework.stereotype.Service;

@Service
public class RecordService {

    private final RecordRepository recordRepository;
    private final RecordCache recordCache;
    private final UrlHasher urlHasher;

    public RecordService(RecordRepository recordRepository, RecordCache recordCache, UrlHasher urlHasher) {
        this.recordRepository = recordRepository;
        this.recordCache = recordCache;
        this.urlHasher = urlHasher;
    }

    public Record addRecord(String url) {
        byte[] urlhash = urlHasher.hash(url);
        String shortcode = recordCache.getShortcode(urlhash);
        if (shortcode == null) {
            shortcode = recordRepository.insertRecord(urlhash, url);
            recordCache.putShortcode(urlhash, shortcode);
        }
        recordCache.putUrl(shortcode, url);
        return recordRepository.findById(shortcode).orElseThrow();
    }

    public Record getRecord(String shortcode) {
        return recordRepository.findById(shortcode).orElse(null);
    }

    public String resolveUrl(String shortcode) {
        String url = resolve(shortcode);
        if (url != null) {
            recordCache.touch(shortcode);
        }
        return url;
    }

    private String resolve(String shortcode) {
        String cachedUrl = recordCache.getUrl(shortcode);
        if (cachedUrl != null) {
            return cachedUrl;
        }
        Record record = recordRepository.findById(shortcode).orElse(null);
        if (record == null) {
            return null;
        }
        recordCache.putUrl(shortcode, record.url());
        return record.url();
    }
}
