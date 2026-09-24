package dev.dukecvar.dinkylink.workers.lasttouched

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
class LastTouchedRepositoryTest {

	@Autowired
	private lateinit var lastTouchedRepository: LastTouchedRepository

	@Autowired
	private lateinit var jdbcTemplate: JdbcTemplate

	private val insertedShortcodes = mutableListOf<String>()

	@AfterEach
	fun cleanUp() {
		if (insertedShortcodes.isNotEmpty()) {
			jdbcTemplate.update(
				"DELETE FROM records WHERE shortcode = ANY(?)",
				insertedShortcodes.toTypedArray(),
			)
		}
		insertedShortcodes.clear()
	}

	private fun insertRecord(shortcode: String, lastTouched: OffsetDateTime) {
		jdbcTemplate.update(
			"INSERT INTO records (shortcode, urlhash, url, last_touched_timestamp) VALUES (?, ?, ?, ?)",
			shortcode,
			UUID.randomUUID().toString().toByteArray(),
			"https://example.com/${UUID.randomUUID()}",
			lastTouched,
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
	fun `updates the last-touched timestamp for a known shortcode`() {
		val shortcode = UUID.randomUUID().toString().take(8)
		insertRecord(shortcode, OffsetDateTime.now().minusYears(1))
		val newTimestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)

		lastTouchedRepository.updateLastTouched(mapOf(shortcode to newTimestamp))

		assertEquals(newTimestamp.toInstant(), lastTouchedOf(shortcode).toInstant())
	}

	@Test
	fun `updates multiple shortcodes in one batch`() {
		val shortcodeA = UUID.randomUUID().toString().take(8)
		val shortcodeB = UUID.randomUUID().toString().take(8)
		insertRecord(shortcodeA, OffsetDateTime.now().minusYears(1))
		insertRecord(shortcodeB, OffsetDateTime.now().minusYears(1))
		val timestampA = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS)
		val timestampB = timestampA.plusMinutes(1)

		lastTouchedRepository.updateLastTouched(mapOf(shortcodeA to timestampA, shortcodeB to timestampB))

		assertEquals(timestampA.toInstant(), lastTouchedOf(shortcodeA).toInstant())
		assertEquals(timestampB.toInstant(), lastTouchedOf(shortcodeB).toInstant())
	}

	@Test
	fun `does nothing for an empty batch`() {
		lastTouchedRepository.updateLastTouched(emptyMap())
	}

	@Test
	fun `silently ignores an unknown shortcode`() {
		lastTouchedRepository.updateLastTouched(mapOf("00000000" to OffsetDateTime.now()))
	}
}
