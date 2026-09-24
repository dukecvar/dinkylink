package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
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

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@AfterEach
	fun cleanUp() {
		redisTemplate.delete(RecordCache.LAST_TOUCHED_LIVE_KEY)
	}

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

	@Test
	fun `resolveUrl records the shortcode in the last-touched live bucket`() {
		val url = uniqueUrl()
		val added = recordService.addRecord(url)

		recordService.resolveUrl(added.shortcode)

		val touched = redisTemplate.opsForHash<String, String>()
			.get(RecordCache.LAST_TOUCHED_LIVE_KEY, added.shortcode)
		assertNotNull(touched)
	}

	@Test
	fun `resolveUrl does not touch the last-touched bucket for an unknown shortcode`() {
		recordService.resolveUrl("00000000")

		val touched = redisTemplate.opsForHash<String, String>()
			.get(RecordCache.LAST_TOUCHED_LIVE_KEY, "00000000")
		assertNull(touched)
	}
}
