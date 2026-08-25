package xyz.robinjoon.notionblog.adapter.input.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotionPresentationAssetsContractTest {
    @Test
    fun `ships the versioned notion stylesheet with semantic themes responsive layout and block coverage`() {
        val css = resourceText("static/presentation/notion/v1/notion.css")

        assertThat(css).contains(
            ":root",
            "--notion-page",
            "--notion-text",
            "--notion-surface",
            "--notion-border",
            "--notion-focus",
            "--notion-content-width",
            "--notion-font-sans",
            "--notion-media-max-width",
            ".notion-color-mode-light",
            ".notion-color-mode-dark",
            "@media (prefers-color-scheme: dark)",
            "@media (prefers-contrast: more)",
            "@media (prefers-reduced-motion: reduce)",
            ".notion-content-width-wide",
            ".notion-density-compact",
            ".notion-title",
            ".notion-heading-toggle",
            ".notion-columns",
            ".notion-column",
            ".notion-column-width-5",
            ".notion-column-width-100",
            "@media (max-width: 720px)",
            ".notion-tabs",
            "[role=tab]",
            "[role=tabpanel]",
            ".notion-table-wrapper",
            ".notion-table",
            ".notion-breadcrumb",
            ".notion-toc",
            ".notion-figure",
            ".notion-caption",
            ".notion-bookmark",
            ".notion-link-preview",
            ".notion-embed",
            ".notion-document-reference",
            ".notion-synced-block",
            ".notion-template",
            ".notion-meeting-notes",
            ".notion-unsupported",
            ".notion-sr-only",
            "@media print",
        )

        val palette = listOf("default", "gray", "brown", "orange", "yellow", "green", "blue", "purple", "pink", "red")
        palette.forEach { color ->
            assertThat(css).contains(".notion-color-$color", ".notion-background-$color")
        }
        assertThat(css).doesNotContain("--notion-column-count", "--notion-column-ratio")
    }

    @Test
    fun `ships a self contained progressive tabs enhancement without dynamic code execution or network access`() {
        val script = resourceText("static/presentation/notion/v1/notion.js")

        assertThat(script).contains("DOMContentLoaded", "ArrowRight", "ArrowLeft", "Home", "End", "Enter", " ")
        assertThat(script).contains("aria-selected", "aria-controls", "hidden", "role=\"tablist\"")
        assertThat(script).doesNotContain("eval(", "Function(", "fetch(", "XMLHttpRequest", "<script", "onkeydown=")
    }

    private fun resourceText(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)) {
        "Missing presentation asset: $path"
    }.readText()
}
