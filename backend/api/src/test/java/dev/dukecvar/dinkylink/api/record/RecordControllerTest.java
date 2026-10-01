package dev.dukecvar.dinkylink.api.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecordController.class)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordService recordService;

    @Test
    @DisplayName("creates a short url and returns 201")
    void createsAShortUrlAndReturns201() throws Exception {
        String url = "https://example.com/some/long/path";
        Record record = new Record("abc12345", new byte[16], url, OffsetDateTime.now());
        when(recordService.addRecord(url)).thenReturn(record);

        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"" + url + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(content().json("{\"url\":\"" + url + "\",\"shortURL\":\"http://localhost:8080/abc12345\"}"));
    }

    @Test
    @DisplayName("rejects a blank url with 400")
    void rejectsABlankUrlWith400() throws Exception {
        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":\"  \",\"error\":\"url must not be blank\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("rejects a request body missing the url field with 400")
    void rejectsARequestBodyMissingTheUrlFieldWith400() throws Exception {
        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":null,\"error\":\"url must not be blank\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("rejects a url longer than 2048 characters with 400")
    void rejectsAUrlLongerThan2048CharactersWith400() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/")
                .contentType("application/json")
                .content("{\"url\":\"" + tooLong + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"url\":\"" + tooLong + "\",\"error\":\"url must be 2048 characters or fewer\"}"));

        verifyNoInteractions(recordService);
    }

    @Test
    @DisplayName("redirects to the original url with 300 when the shortcode is found")
    void redirectsToTheOriginalUrlWith300WhenTheShortcodeIsFound() throws Exception {
        when(recordService.resolveUrl("abc12345")).thenReturn("https://example.com/target");

        mockMvc.perform(get("/abc12345"))
            .andExpect(status().isMultipleChoices())
            .andExpect(header().string("Location", "https://example.com/target"));
    }

    @Test
    @DisplayName("returns 404 when the shortcode is not found")
    void returns404WhenTheShortcodeIsNotFound() throws Exception {
        when(recordService.resolveUrl("00000000")).thenReturn(null);

        mockMvc.perform(get("/00000000"))
            .andExpect(status().isNotFound());
    }
}
