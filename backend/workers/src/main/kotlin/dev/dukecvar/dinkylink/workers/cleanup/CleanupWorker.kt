package dev.dukecvar.dinkylink.workers.cleanup

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.Period

/**
 * Once a day, deletes records that haven't been touched in over a year.
 * See "Clean up worker" in docs/design.md.
 */
@Component
class CleanupWorker(
	private val cleanupRepository: CleanupRepository,
) {

	companion object {
		private val logger = LoggerFactory.getLogger(CleanupWorker::class.java)
		private val RETENTION: Period = Period.ofYears(1)
	}

	@Scheduled(cron = "\${dinkylink.workers.cleanup.cron:0 0 2 * * *}")
	fun cleanUp() {
		val threshold = OffsetDateTime.now().minus(RETENTION)
		val deleted = cleanupRepository.deleteRecordsLastTouchedBefore(threshold)
		logger.info("Deleted {} record(s) last touched before {}", deleted, threshold)
	}
}
