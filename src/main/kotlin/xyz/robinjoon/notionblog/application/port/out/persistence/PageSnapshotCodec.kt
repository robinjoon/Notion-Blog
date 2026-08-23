package xyz.robinjoon.notionblog.application.port.out.persistence

import xyz.robinjoon.notionblog.domain.model.NotionBlock

interface PageSnapshotCodec {
    fun encode(blocks: List<NotionBlock>): String

    fun decode(snapshotJson: String): List<NotionBlock>
}
