package dev.dukecvar.dinkylink.api.record;

import com.google.common.hash.Hashing;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class UrlHasher {
    public byte[] hash(String url) {
        return Hashing.murmur3_128().hashString(url, StandardCharsets.UTF_8).asBytes();
    }
}
