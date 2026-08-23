package xyz.robinjoon.notionblog.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SlugTest {
    @Test
    fun `preserves Korean and other non-Latin letters while normalizing separators`() {
        assertThat(Slug.fromTitle("첫 번째 글 — Привет 世界")).isEqualTo("첫-번째-글-привет-世界")
    }

    @Test
    fun `falls back to a stable page suffix when a title contains no letters or numbers`() {
        assertThat(Slug.fromTitle("!!!", "12345678-90ab-cdef-1234-567890abcdef"))
            .isEqualTo("page-12345678")
    }

    @Test
    fun `adds a stable page suffix and then a counter for slug collisions`() {
        val taken = setOf("hello", "hello-abcdef12")

        assertThat(Slug.unique("hello", "abcdef12-3456-7890-abcd-ef1234567890", taken::contains))
            .isEqualTo("hello-abcdef12-2")
    }
}
