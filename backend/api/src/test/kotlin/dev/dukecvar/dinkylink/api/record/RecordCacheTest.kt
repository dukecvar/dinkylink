package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class RecordCacheTest {

	@Autowired
	private lateinit var recordCache: RecordCache

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	private val usedKeys = mutableListOf<String>()

	@AfterEach
	fun cleanUp() {
		if (usedKeys.isNotEmpty()) {
			redisTemplate.delete(usedKeys)
		}
		usedKeys.clear()
	}

	private fun randomUrlhash(): ByteArray = UUID.randomUUID().toString().toByteArray()

	@Test
	fun `stores and retrieves a shortcode by urlhash`() {
		val urlhash = randomUrlhash()
		usedKeys.add("shortcodes:${java.util.HexFormat.of().formatHex(urlhash)}")

		recordCache.putShortcode(urlhash, "abc12345")

		assertEquals("abc12345", recordCache.getShortcode(urlhash))
	}

	@Test
	fun `returns null for an unknown urlhash`() {
		assertNull(recordCache.getShortcode(randomUrlhash()))
	}

	@Test
	fun `stores and retrieves a url by shortcode`() {
		val shortcode = UUID.randomUUID().toString().take(8)
		usedKeys.add("urls:$shortcode")

		recordCache.putUrl(shortcode, "https://example.com/foo")

		assertEquals("https://example.com/foo", recordCache.getUrl(shortcode))
	}

	@Test
	fun `returns null for an unknown shortcode`() {
		assertNull(recordCache.getUrl(UUID.randomUUID().toString().take(8)))
	}

	@Test
	fun `refreshes the url TTL on a cache hit`() {
		val shortcode = UUID.randomUUID().toString().take(8)
		val key = "urls:$shortcode"
		usedKeys.add(key)

		recordCache.putUrl(shortcode, "https://example.com/foo")
		redisTemplate.expire(key, Duration.ofSeconds(5))

		recordCache.getUrl(shortcode)

		val remaining = redisTemplate.getExpire(key, TimeUnit.DAYS)
		assertTrue(remaining > 300, "expected TTL to be refreshed close to 365 days, was $remaining days")
	}
}
