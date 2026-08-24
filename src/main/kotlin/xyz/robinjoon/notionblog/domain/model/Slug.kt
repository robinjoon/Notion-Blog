package xyz.robinjoon.notionblog.domain.model

import java.util.Locale

object Slug {
    private val nonSlugCharacters = Regex("[^\\p{L}\\p{N}]+")

    fun fromTitle(title: String, pageId: String? = null): String {
        val slug = title
            .lowercase(Locale.ROOT)
            .replace(nonSlugCharacters, "-")
            .trim('-')

        if (slug.isNotEmpty()) {
            return slug
        }

        val suffix = pageId?.replace("-", "")?.take(8).orEmpty()
        return if (suffix.isEmpty()) "page" else "page-$suffix"
    }

    fun unique(base: String, pageId: String, exists: (String) -> Boolean): String {
        if (!exists(base)) {
            return base
        }

        val suffix = pageId.replace("-", "").take(8)
        val candidate = if (suffix.isEmpty()) "$base-page" else "$base-$suffix"
        if (!exists(candidate)) {
            return candidate
        }

        var counter = 2
        while (exists("$candidate-$counter")) {
            counter += 1
        }
        return "$candidate-$counter"
    }
}
