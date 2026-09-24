package dev.dukecvar.dinkylink.workers.lasttouched

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class LastTouchedFlushWorkerTest {

	@Autowired
	private lateinit var flushWorker: LastTouchedFlushWorker

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	private val insertedShortcodes = mutableListOf<String>()

	@BeforeEach
	fun clearLiveBucket() {
		redisTemplate.delete(LastTouchedFlushWorker.LIVE_KEY)
	}

	@AfterEach
	fun cleanUp() {
		redisTemplate.delete(LastTouchedFlushWorker.LIVE_KEY)
		val flushingKeys = redisTemplate.keys("last-touched:flushing:*")
		if (!flushingKeys.isNullOrEmpty()) {
			redisTemplate.delete(flushingKeys)
		}
		if (insertedShortcodes.isNotEmpty()) {
			jdbcTemplate.update(
				"DELETE FROM records WHERE shortcode = ANY(?)",
				insertedShortcodes.toTypedArray(),
			)
		}
		insertedShortcodes.clear()
	}

	private fun insertRecord(shortcode: String) {
		jdbcTemplate.update(
			"INSERT INTO records (shortcode, urlhash, url, last_touched_timestamp) VALUES (?, ?, ?, ?)",
			shortcode,
			UUID.randomUUID().toString().toByteArray(),
			"https://example.com/${UUID.randomUUID()}",
			OffsetDateTime.now().minusYears(1),
		)
		insertedShortcodes.add(shortcode)
	}

	private fun lastTouchedOf(shortcode: String): OffsetDateTime =
		jdbcTemplate.queryForObject(
			"SELECT last_touched_timestamp FROM records WHERE shortcode = ?",
			OffsetDateTime::class.java,
			shortcode,
		)!!

	@Test
	fun `does nothing when the live bucket is empty`() {
		flushWorker.flush()
	}

	@Test
	fun `flushes a live entry into the database and clears the bucket`() {
		val shortcode = UUID.randomUUID().toString().take(8)
		insertRecord(shortcode)
		val touchedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)
		redisTemplate.opsForHash<String, String>().put(LastTouchedFlushWorker.LIVE_KEY, shortcode, touchedAt.toString())

		flushWorker.flush()

		assertEquals(touchedAt.toInstant(), lastTouchedOf(shortcode).toInstant())
		assertNull(redisTemplate.opsForHash<String, String>().get(LastTouchedFlushWorker.LIVE_KEY, shortcode))
		assertTrue(redisTemplate.keys("last-touched:flushing:*").isNullOrEmpty())
	}

	@Test
	fun `flushes more than one batch's worth of entries`() {
		val shortcodes = (1..5).map { UUID.randomUUID().toString().take(8) }
		shortcodes.forEach { insertRecord(it) }
		val touchedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)
		val hashOps = redisTemplate.opsForHash<String, String>()
		shortcodes.forEach { hashOps.put(LastTouchedFlushWorker.LIVE_KEY, it, touchedAt.toString()) }

		flushWorker.flush()

		shortcodes.forEach { shortcode ->
			assertEquals(touchedAt.toInstant(), lastTouchedOf(shortcode).toInstant())
			assertNull(hashOps.get(LastTouchedFlushWorker.LIVE_KEY, shortcode))
		}
	}

	@Test
	fun `keeps draining without waiting when a new live bucket appears mid-flush`() {
		val shortcodeA = UUID.randomUUID().toString().take(8)
		val shortcodeB = UUID.randomUUID().toString().take(8)
		insertRecord(shortcodeA)
		insertRecord(shortcodeB)
		val touchedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)
		val hashOps = redisTemplate.opsForHash<String, String>()
		hashOps.put(LastTouchedFlushWorker.LIVE_KEY, shortcodeA, touchedAt.toString())

		// As soon as the first rotation is visible (a flushing:* key exists),
		// repopulate the live bucket before flush() finishes its loop. If
		// flush() only drained once per call, shortcodeB would still be
		// sitting in the live bucket, untouched, after flush() returns.
		val producer = Thread {
			val deadline = System.currentTimeMillis() + 5000
			while (System.currentTimeMillis() < deadline && redisTemplate.keys("last-touched:flushing:*").isNullOrEmpty()) {
				Thread.sleep(1)
			}
			hashOps.put(LastTouchedFlushWorker.LIVE_KEY, shortcodeB, touchedAt.toString())
		}
		producer.start()

		flushWorker.flush()
		producer.join(5000)

		assertEquals(touchedAt.toInstant(), lastTouchedOf(shortcodeA).toInstant())
		assertEquals(touchedAt.toInstant(), lastTouchedOf(shortcodeB).toInstant())
		assertNull(hashOps.get(LastTouchedFlushWorker.LIVE_KEY, shortcodeB))
		assertTrue(redisTemplate.keys("last-touched:flushing:*").isNullOrEmpty())
	}
}
