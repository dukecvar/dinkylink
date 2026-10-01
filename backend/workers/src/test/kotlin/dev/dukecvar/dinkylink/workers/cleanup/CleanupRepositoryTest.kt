package dev.dukecvar.dinkylink.workers.cleanup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class CleanupRepositoryTest {

	@Autowired
	private lateinit var cleanupRepository: CleanupRepository

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

	private fun findShortcode(shortcode: String): String? =
		jdbcTemplate.query("SELECT shortcode FROM records WHERE shortcode = ?", { rs, _ -> rs.getString("shortcode") }, shortcode)
			.firstOrNull()

	@Test
	fun `deletes records last touched before the threshold`() {
		val stale = UUID.randomUUID().toString().take(8)
		insertRecord(stale, OffsetDateTime.now().minusYears(2))
		val threshold = OffsetDateTime.now().minusYears(1)

		val deleted = cleanupRepository.deleteRecordsLastTouchedBefore(threshold)

		assertEquals(1, deleted)
		assertNull(findShortcode(stale))
	}

	@Test
	fun `does not delete records last touched at or after the threshold`() {
		val fresh = UUID.randomUUID().toString().take(8)
		insertRecord(fresh, OffsetDateTime.now())
		val threshold = OffsetDateTime.now().minusYears(1)

		cleanupRepository.deleteRecordsLastTouchedBefore(threshold)

		assertNotNull(findShortcode(fresh))
	}

	@Test
	fun `returns zero when nothing matches`() {
		val fresh = UUID.randomUUID().toString().take(8)
		insertRecord(fresh, OffsetDateTime.now())
		val threshold = OffsetDateTime.now().minusYears(1)

		val deleted = cleanupRepository.deleteRecordsLastTouchedBefore(threshold)

		assertEquals(0, deleted)
	}
}
