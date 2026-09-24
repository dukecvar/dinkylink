package dev.dukecvar.dinkylink.api.record

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.HexFormat

@Component
class RecordCache(
	private val redisTemplate: StringRedisTemplate,
) {

	companion object {
		private val TTL: Duration = Duration.ofDays(365)
	}

	fun getShortcode(urlhash: ByteArray): String? = get(shortcodeKey(urlhash))

	fun putShortcode(urlhash: ByteArray, shortcode: String) = put(shortcodeKey(urlhash), shortcode)

	fun getUrl(shortcode: String): String? = get(urlKey(shortcode))

	fun putUrl(shortcode: String, url: String) = put(urlKey(shortcode), url)

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
