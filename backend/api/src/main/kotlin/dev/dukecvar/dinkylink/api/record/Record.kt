package dev.dukecvar.dinkylink.api.record

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table("records")
data class Record(
	@Id
	val shortcode: String,
	val urlhash: ByteArray,
	val url: String,
	val lastTouchedTimestamp: OffsetDateTime,
)
