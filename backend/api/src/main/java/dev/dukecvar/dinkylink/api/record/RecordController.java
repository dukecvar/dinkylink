package dev.dukecvar.dinkylink.api.record;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record CreateShortUrlRequest(String url) {}
record CreateShortUrlResponse(String url, String shortURL) {}
record CreateShortUrlErrorResponse(String url, String error) {}

@RestController
public class RecordController {

    private final RecordService recordService;
    private final String baseUrl;

    public RecordController(RecordService recordService, @Value("${dinkylink.base-url}") String baseUrl) {
        this.recordService = recordService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/")
    public ResponseEntity<Object> createShortUrl(@RequestBody CreateShortUrlRequest request) {
        String url = request.url();

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(new CreateShortUrlErrorResponse(request.url(), "url must not be blank"));
        }
        if (url.length() > 2048) {
            return ResponseEntity.badRequest().body(new CreateShortUrlErrorResponse(url, "url must be 2048 characters or fewer"));
        }

        Record record = recordService.addRecord(url);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CreateShortUrlResponse(record.url(), baseUrl + "/" + record.shortcode()));
    }

    @GetMapping("/{shortcode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortcode) {
        String url = recordService.resolveUrl(shortcode);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MULTIPLE_CHOICES)
            .header("Location", url)
            .build();
    }
}
