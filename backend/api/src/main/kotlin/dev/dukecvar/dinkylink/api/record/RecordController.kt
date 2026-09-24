package dev.dukecvar.dinkylink.api.record

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateShortUrlRequest(val url: String?)
data class CreateShortUrlResponse(val url: String, val shortURL: String)
data class CreateShortUrlErrorResponse(val url: String?, val error: String)

@RestController
class RecordController(
	private val recordService: RecordService,
	@Value("\${dinkylink.base-url}") private val baseUrl: String,
) {

	@PostMapping("/")
	fun createShortUrl(@RequestBody request: CreateShortUrlRequest): ResponseEntity<Any> {
		val url = request.url

		if (url.isNullOrBlank()) {
			return ResponseEntity.badRequest().body(CreateShortUrlErrorResponse(request.url, "url must not be blank"))
		}
		if (url.length > 2048) {
			return ResponseEntity.badRequest().body(CreateShortUrlErrorResponse(url, "url must be 2048 characters or fewer"))
		}

		val record = recordService.addRecord(url)
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CreateShortUrlResponse(record.url, "$baseUrl/${record.shortcode}"))
	}

	@GetMapping("/{shortcode}")
	fun redirect(@PathVariable shortcode: String): ResponseEntity<Void> {
		val url = recordService.resolveUrl(shortcode)
			?: return ResponseEntity.notFound().build()

		return ResponseEntity.status(HttpStatus.MULTIPLE_CHOICES)
			.header("Location", url)
			.build()
	}
}
