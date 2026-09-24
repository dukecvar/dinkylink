package dev.dukecvar.dinkylink.workers.cleanup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class CleanupWorkerTest {

	@Autowired
	private lateinit var cleanupWorker: CleanupWorker

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
	fun `deletes records untouched for over a year and keeps recent ones`() {
		val stale = UUID.randomUUID().toString().take(8)
		val fresh = UUID.randomUUID().toString().take(8)
		insertRecord(stale, OffsetDateTime.now().minusYears(1).minusDays(1))
		insertRecord(fresh, OffsetDateTime.now())

		cleanupWorker.cleanUp()

		assertNull(findShortcode(stale))
		assertNotNull(findShortcode(fresh))
	}
}
