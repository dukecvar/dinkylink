package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlHasherTest {

    private final UrlHasher hasher = new UrlHasher();

    @Test
    @DisplayName("hashes to 16 bytes")
    void hashesTo16Bytes() {
        assertThat(hasher.hash("https://example.com")).hasSize(16);
    }

    @Test
    @DisplayName("same url hashes deterministically")
    void sameUrlHashesDeterministically() {
        String url = "https://example.com/a";
        assertThat(hasher.hash(url)).isEqualTo(hasher.hash(url));
    }

    @Test
    @DisplayName("different urls hash differently")
    void differentUrlsHashDifferently() {
        assertThat(hasher.hash("https://example.com/a"))
            .isNotEqualTo(hasher.hash("https://example.com/b"));
    }
}
