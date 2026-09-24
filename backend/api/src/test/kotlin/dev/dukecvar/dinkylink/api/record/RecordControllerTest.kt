package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime

@WebMvcTest(RecordController::class)
class RecordControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockitoBean
	private lateinit var recordService: RecordService

	@Test
	fun `creates a short url and returns 201`() {
		val url = "https://example.com/some/long/path"
		val record = Record("abc12345", ByteArray(16), url, OffsetDateTime.now())
		`when`(recordService.addRecord(url)).thenReturn(record)

		mockMvc.perform(
			post("/")
				.contentType("application/json")
				.content("""{"url":"$url"}""")
		)
			.andExpect(status().isCreated)
			.andExpect(content().json("""{"url":"$url","shortURL":"http://localhost:8080/abc12345"}"""))
	}

	@Test
	fun `rejects a blank url with 400`() {
		mockMvc.perform(
			post("/")
				.contentType("application/json")
				.content("""{"url":"  "}""")
		)
			.andExpect(status().isBadRequest)
			.andExpect(content().json("""{"url":"  ","error":"url must not be blank"}"""))

		verifyNoInteractions(recordService)
	}

	@Test
	fun `rejects a url longer than 2048 characters with 400`() {
		val tooLong = "https://example.com/" + "a".repeat(2048)

		mockMvc.perform(
			post("/")
				.contentType("application/json")
				.content("""{"url":"$tooLong"}""")
		)
			.andExpect(status().isBadRequest)
			.andExpect(content().json("""{"url":"$tooLong","error":"url must be 2048 characters or fewer"}"""))

		verifyNoInteractions(recordService)
	}

	@Test
	fun `redirects to the original url with 300 when the shortcode is found`() {
		`when`(recordService.resolveUrl("abc12345")).thenReturn("https://example.com/target")

		mockMvc.perform(get("/abc12345"))
			.andExpect(status().isMultipleChoices)
			.andExpect(header().string("Location", "https://example.com/target"))
	}

	@Test
	fun `returns 404 when the shortcode is not found`() {
		`when`(recordService.resolveUrl("00000000")).thenReturn(null)

		mockMvc.perform(get("/00000000"))
			.andExpect(status().isNotFound)
	}
}
