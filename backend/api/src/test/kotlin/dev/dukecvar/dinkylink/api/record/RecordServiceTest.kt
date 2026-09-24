package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Transactional
class RecordServiceTest {

	@Autowired
	private lateinit var recordService: RecordService

	@Test
	fun `adds and retrieves a record by shortcode`() {
		val url = "https://example.com/some/long/path?query=1"

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
}
