package dev.dukecvar.dinkylink.workers.lasttouched

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class LastTouchedRepository(
	private val jdbcTemplate: JdbcTemplate,
) {

	fun updateLastTouched(timestampsByShortcode: Map<String, OffsetDateTime>) {
		if (timestampsByShortcode.isEmpty()) return

		val entries = timestampsByShortcode.entries.toList()
		jdbcTemplate.batchUpdate(
			"UPDATE records SET last_touched_timestamp = ? WHERE shortcode = ?",
			entries,
			entries.size,
		) { ps, entry ->
			ps.setObject(1, entry.value)
			ps.setString(2, entry.key)
		}
	}
}
