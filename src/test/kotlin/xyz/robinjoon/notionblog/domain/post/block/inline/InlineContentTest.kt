package xyz.robinjoon.notionblog.domain.post.block.inline

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.notionblog.domain.post.block.style.ColorToken

class InlineContentTest {
    @Test
    fun `keeps foreground and background annotations independently`() {
        val annotations = TextAnnotations(foreground = ColorToken.RED, background = ColorToken.BLUE)

        assertThat(annotations.foreground).isEqualTo(ColorToken.RED)
        assertThat(annotations.background).isEqualTo(ColorToken.BLUE)
    }
}
