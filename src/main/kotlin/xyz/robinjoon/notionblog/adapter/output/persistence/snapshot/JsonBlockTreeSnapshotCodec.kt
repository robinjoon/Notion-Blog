package xyz.robinjoon.notionblog.adapter.output.persistence.snapshot

import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.notionblog.domain.post.block.BlockTree

class JsonBlockTreeSnapshotCodec(
    private val mapper: JsonMapper = JsonMapper.builder().build(),
) {
    private val blockTreeMapper = BlockTreeSnapshotMapper()
    fun encode(tree: BlockTree): String = mapper.writeValueAsString(blockTreeMapper.toJson(tree))

    fun decode(snapshotJson: String): BlockTree = try {
        blockTreeMapper.fromJson(mapper.readTree(snapshotJson))
    } catch (exception: IllegalArgumentException) {
        throw exception
    } catch (exception: Exception) {
        throw IllegalArgumentException("invalid block tree snapshot", exception)
    }
}
