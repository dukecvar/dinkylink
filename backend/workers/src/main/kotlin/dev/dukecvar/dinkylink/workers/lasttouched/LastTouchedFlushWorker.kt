package dev.dukecvar.dinkylink.workers.lasttouched

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Drains the `last-touched:live` Redis hash (shortcode -> last-accessed
 * timestamp, populated by backend/api on every redirect) into Postgres.
 * See "Last touched worker" in docs/design.md.
 */
@Component
class LastTouchedFlushWorker(
	private val redisTemplate: StringRedisTemplate,
	private val lastTouchedRepository: LastTouchedRepository,
) {

	companion object {
		private val logger = LoggerFactory.getLogger(LastTouchedFlushWorker::class.java)
		const val LIVE_KEY = "last-touched:live"
		private const val BATCH_SIZE = 1000L
	}

	@Scheduled(fixedDelay = 60_000)
	fun flush() {
		val flushingKey = rotateLiveBucket() ?: return
		try {
			drain(flushingKey)
		} finally {
			redisTemplate.delete(flushingKey)
		}
	}

	private fun rotateLiveBucket(): String? {
		if (redisTemplate.hasKey(LIVE_KEY) != true) {
			return null
		}
		val flushingKey = "last-touched:flushing:${Instant.now().toEpochMilli()}"
		return try {
			redisTemplate.rename(LIVE_KEY, flushingKey)
			flushingKey
		} catch (e: Exception) {
			logger.warn("Skipping this flush cycle; live bucket rename failed", e)
			null
		}
	}

	private fun drain(flushingKey: String) {
		val hashOps = redisTemplate.opsForHash<String, String>()
		val scanOptions = ScanOptions.scanOptions().count(BATCH_SIZE).build()
		var processed = 0

		hashOps.scan(flushingKey, scanOptions).use { cursor ->
			val batch = mutableMapOf<String, String>()
			while (cursor.hasNext()) {
				val entry = cursor.next()
				batch[entry.key] = entry.value
				if (batch.size >= BATCH_SIZE) {
					processed += flushBatch(flushingKey, batch)
				}
			}
			processed += flushBatch(flushingKey, batch)
		}

		logger.info("Flushed {} last-touched record(s)", processed)
	}

	private fun flushBatch(flushingKey: String, batch: MutableMap<String, String>): Int {
		if (batch.isEmpty()) return 0
		val size = batch.size

		lastTouchedRepository.updateLastTouched(batch.mapValues { OffsetDateTime.parse(it.value) })
		redisTemplate.opsForHash<String, String>().delete(flushingKey, *batch.keys.toTypedArray())
		batch.clear()

		return size
	}
}
