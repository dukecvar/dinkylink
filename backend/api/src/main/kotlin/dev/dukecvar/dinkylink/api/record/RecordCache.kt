package dev.dukecvar.dinkylink.api.record

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.util.HexFormat

@Component
class RecordCache(
	private val redisTemplate: StringRedisTemplate,
) {

	companion object {
		private val TTL: Duration = Duration.ofDays(365)
		const val LAST_TOUCHED_LIVE_KEY = "last-touched:live"
	}

	fun getShortcode(urlhash: ByteArray): String? = get(shortcodeKey(urlhash))

	fun putShortcode(urlhash: ByteArray, shortcode: String) = put(shortcodeKey(urlhash), shortcode)

	fun getUrl(shortcode: String): String? = get(urlKey(shortcode))

	fun putUrl(shortcode: String, url: String) = put(urlKey(shortcode), url)

	fun touch(shortcode: String) {
		redisTemplate.opsForHash<String, String>().put(LAST_TOUCHED_LIVE_KEY, shortcode, OffsetDateTime.now().toString())
	}

	private fun get(key: String): String? {
		val value = redisTemplate.opsForValue().get(key)
		if (value != null) {
			redisTemplate.expire(key, TTL)
		}
		return value
	}

	private fun put(key: String, value: String) {
		redisTemplate.opsForValue().set(key, value, TTL)
	}

	private fun shortcodeKey(urlhash: ByteArray) = "shortcodes:${HexFormat.of().formatHex(urlhash)}"

	private fun urlKey(shortcode: String) = "urls:$shortcode"
}
