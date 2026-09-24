package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Transactional
class RecordServiceTest {

	@Autowired
	private lateinit var recordService: RecordService

	@Autowired
	private lateinit var recordRepository: RecordRepository

	@Autowired
	private lateinit var urlHasher: UrlHasher

	private fun uniqueUrl() = "https://example.com/${UUID.randomUUID()}"

	@Test
	fun `adds and retrieves a record by shortcode`() {
		val url = uniqueUrl()

		val added = recordService.addRecord(url)
		assertEquals(8, added.shortcode.length)
		assertEquals(url, added.url)

		val retrieved = recordService.getRecord(added.shortcode)

		assertNotNull(retrieved)
		assertEquals(added.shortcode, retrieved.shortcode)
		assertEquals(url, retrieved.url)
		assertEquals(added.urlhash.toList(), retrieved.urlhash.toList())
	}

	@Test
	fun `retrieving an unknown shortcode returns null`() {
		assertNull(recordService.getRecord("00000000"))
	}

	@Test
	fun `adding the same URL twice returns the same shortcode`() {
		val url = uniqueUrl()

		val first = recordService.addRecord(url)
		val second = recordService.addRecord(url)

		assertEquals(first.shortcode, second.shortcode)
	}

	@Test
	fun `resolveUrl returns the url for a freshly added record`() {
		val url = uniqueUrl()
		val added = recordService.addRecord(url)

		assertEquals(url, recordService.resolveUrl(added.shortcode))
	}

	@Test
	fun `resolveUrl falls back to the database when the url isn't cached`() {
		val url = uniqueUrl()
		val urlhash = urlHasher.hash(url)
		val shortcode = recordRepository.insertRecord(urlhash, url)

		assertEquals(url, recordService.resolveUrl(shortcode))
	}

	@Test
	fun `resolveUrl returns null for an unknown shortcode`() {
		assertNull(recordService.resolveUrl("00000000"))
	}
}
