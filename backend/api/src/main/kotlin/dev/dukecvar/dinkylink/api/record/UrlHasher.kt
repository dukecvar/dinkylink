package dev.dukecvar.dinkylink.api.record

import com.google.common.hash.Hashing
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class UrlHasher {

	fun hash(url: String): ByteArray =
		Hashing.murmur3_128().hashString(url, StandardCharsets.UTF_8).asBytes()
}
