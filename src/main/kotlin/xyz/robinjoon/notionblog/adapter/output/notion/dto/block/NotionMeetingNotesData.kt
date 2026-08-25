package xyz.robinjoon.notionblog.adapter.output.notion.dto.block

import xyz.robinjoon.notionblog.adapter.output.notion.dto.richtext.NotionRichTextEnvelope

data class NotionMeetingNotesData(
    val title: List<NotionRichTextEnvelope> = emptyList(),
    val status: String? = null,
    val children: NotionMeetingNotesChildrenResponse? = null,
)

data class NotionMeetingNotesChildrenResponse(
    val summaryBlockId: String? = null,
    val notesBlockId: String? = null,
)
