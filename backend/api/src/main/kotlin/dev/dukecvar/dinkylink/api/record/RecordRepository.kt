package dev.dukecvar.dinkylink.api.record

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface RecordRepository : CrudRepository<Record, String> {

	@Query("SELECT insert_record(:urlhash, :url)")
	fun insertRecord(@Param("urlhash") urlhash: ByteArray, @Param("url") url: String): String
}
