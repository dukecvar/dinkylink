package dev.dukecvar.dinkylink.api.record

import org.springframework.stereotype.Service

@Service
class RecordService(
	private val recordRepository: RecordRepository,
	private val urlHasher: UrlHasher,
) {

	fun addRecord(url: String): Record {
		val urlhash = urlHasher.hash(url)
		val shortcode = recordRepository.insertRecord(urlhash, url)
		return recordRepository.findById(shortcode).orElseThrow()
	}

	fun getRecord(shortcode: String): Record? =
		recordRepository.findById(shortcode).orElse(null)
}
