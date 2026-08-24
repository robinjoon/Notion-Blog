package xyz.robinjoon.notionblog.adapter.out.persistence

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DefaultTyping
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule
import xyz.robinjoon.notionblog.application.port.out.persistence.PageSnapshotCodec
import xyz.robinjoon.notionblog.domain.model.NotionBlock

class TaggedPageSnapshotCodec : PageSnapshotCodec {
    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .activateDefaultTypingAsProperty(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("xyz.robinjoon.notionblog.domain.model.")
                .allowIfSubType("java.util.")
                .allowIfSubType("kotlin.collections.")
                .build(),
            DefaultTyping.NON_FINAL,
            "type",
        )
        .build()

    override fun encode(blocks: List<NotionBlock>): String = mapper.writeValueAsString(blocks)

    override fun decode(snapshotJson: String): List<NotionBlock> = mapper.readValue(snapshotJson, object : TypeReference<List<NotionBlock>>() {})
}
