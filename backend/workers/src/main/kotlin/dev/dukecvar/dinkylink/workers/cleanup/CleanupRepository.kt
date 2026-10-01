package dev.dukecvar.dinkylink.workers.cleanup

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class CleanupRepository(
	private val jdbcTemplate: JdbcTemplate,
) {

	fun deleteRecordsLastTouchedBefore(threshold: OffsetDateTime): Int =
		jdbcTemplate.update("DELETE FROM records WHERE last_touched_timestamp < ?", threshold)
}
