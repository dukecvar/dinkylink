package dev.dukecvar.dinkylink.workers.cleanup

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.Period

/**
 * Once a day, deletes records that haven't been touched within the
 * retention period. See "Clean up worker" in docs/design.md.
 */
@Component
class CleanupWorker(
	private val cleanupRepository: CleanupRepository,
	@Value("\${dinkylink.workers.cleanup.retention:P1Y}") retention: String,
) {

	companion object {
		private val logger = LoggerFactory.getLogger(CleanupWorker::class.java)
	}

	private val retention: Period = Period.parse(retention)

	@Scheduled(cron = "\${dinkylink.workers.cleanup.cron:0 0 2 * * *}")
	fun cleanUp() {
		val threshold = OffsetDateTime.now().minus(retention)
		val deleted = cleanupRepository.deleteRecordsLastTouchedBefore(threshold)
		logger.info("Deleted {} record(s) last touched before {}", deleted, threshold)
	}
}
