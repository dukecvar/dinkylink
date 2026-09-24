package dev.dukecvar.dinkylink.api.record

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UrlHasherTest {

	private val hasher = UrlHasher()

	@Test
	fun `hashes to 16 bytes`() {
		val hash = hasher.hash("https://example.com")

		assertEquals(16, hash.size)
	}

	@Test
	fun `same url hashes deterministically`() {
		val url = "https://example.com/a"

		assertEquals(hasher.hash(url).toList(), hasher.hash(url).toList())
	}

	@Test
	fun `different urls hash differently`() {
		val a = hasher.hash("https://example.com/a")
		val b = hasher.hash("https://example.com/b")

		assertNotEquals(a.toList(), b.toList())
	}
}
