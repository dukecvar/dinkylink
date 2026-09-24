package dev.dukecvar.dinkylink.api.record

import org.springframework.stereotype.Service

@Service
class RecordService(
	private val recordRepository: RecordRepository,
	private val recordCache: RecordCache,
	private val urlHasher: UrlHasher,
) {

	fun addRecord(url: String): Record {
		val urlhash = urlHasher.hash(url)
		val shortcode = recordCache.getShortcode(urlhash) ?: run {
			val inserted = recordRepository.insertRecord(urlhash, url)
			recordCache.putShortcode(urlhash, inserted)
			inserted
		}
		recordCache.putUrl(shortcode, url)
		return recordRepository.findById(shortcode).orElseThrow()
	}

	fun getRecord(shortcode: String): Record? =
		recordRepository.findById(shortcode).orElse(null)

	fun resolveUrl(shortcode: String): String? =
		recordCache.getUrl(shortcode) ?: recordRepository.findById(shortcode).orElse(null)?.url?.also {
			recordCache.putUrl(shortcode, it)
		}
}
