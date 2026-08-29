package xyz.robinjoon.notionblog.adapter.output.notion

import tools.jackson.databind.JsonNode
import xyz.robinjoon.notionblog.adapter.output.notion.client.NotionApiClient
import xyz.robinjoon.notionblog.adapter.output.notion.dto.NotionBlockEnvelope
import xyz.robinjoon.notionblog.adapter.output.notion.mapping.NotionBlockMapper
import xyz.robinjoon.notionblog.adapter.output.notion.mapping.NotionBlockMappingException
import xyz.robinjoon.notionblog.adapter.output.notion.mapping.NotionIdNormalizer
import xyz.robinjoon.notionblog.adapter.output.notion.mapping.NotionPageMapper
import xyz.robinjoon.notionblog.application.model.ImportedPost
import xyz.robinjoon.notionblog.application.port.output.source.PostSource
import xyz.robinjoon.notionblog.application.port.output.source.RetryableSourceException
import xyz.robinjoon.notionblog.application.port.output.source.SourceConfigurationException
import xyz.robinjoon.notionblog.application.port.output.source.SourceException
import xyz.robinjoon.notionblog.application.port.output.source.SourceMappingException
import xyz.robinjoon.notionblog.domain.post.block.BlockNode
import xyz.robinjoon.notionblog.domain.post.block.BlockTree
import xyz.robinjoon.notionblog.domain.source.SourceDocumentRef
import xyz.robinjoon.notionblog.domain.source.SourceId
import java.time.Duration

internal class NotionPostSource(
    private val sourceId: SourceId,
    private val client: NotionApiClient,
    private val pageMapper: NotionPageMapper = NotionPageMapper(),
    private val blockMapper: NotionBlockMapper = NotionBlockMapper(sourceId),
    private val maxDepth: Int,
    private val maxBlockCount: Int,
    private val collectionTimeout: Duration,
    private val nanoTime: () -> Long = System::nanoTime,
) : PostSource {
    init {
        require(maxDepth > 0) { "Notion maximum block depth must be positive" }
        require(maxBlockCount > 0) { "Notion maximum block count must be positive" }
        require(collectionTimeout.isPositive) { "Notion collection timeout must be positive" }
    }

    override fun fetch(reference: SourceDocumentRef): ImportedPost {
        if (reference.sourceId != sourceId) {
            throw SourceConfigurationException("Notion source reference belongs to another source")
        }
        val startedAt = nanoTime()
        return try {
            val requestedReference = reference.copy(externalId = NotionIdNormalizer.normalize(reference.externalId))
            val state = TraversalState(mutableSetOf(requestedReference.externalId))
            checkDeadline(startedAt)
            val page = client.fetchPage(requestedReference.externalId)
            checkDeadline(startedAt)
            val metadata = pageMapper.map(page, requestedReference)
            val roots = collectChildren(metadata.sourceDocument.externalId, 1, null, metadata.sourceDocument, startedAt, state)
            ImportedPost(
                sourceDocument = metadata.sourceDocument,
                title = metadata.title,
                publicationStatus = metadata.publicationStatus,
                sourceRevision = metadata.sourceRevision,
                content = BlockTree(roots),
                containedChildren = state.containedChildren,
            )
        } catch (exception: SourceException) {
            throw exception
        } catch (exception: NotionBlockMappingException) {
            throw SourceMappingException("Notion block could not be mapped", exception)
        } catch (exception: IllegalArgumentException) {
            throw SourceMappingException("Notion post content is malformed", exception)
        }
    }

    private fun collectChildren(
        parentBlockId: String,
        depth: Int,
        parentType: String?,
        sourceDocument: SourceDocumentRef,
        startedAt: Long,
        state: TraversalState,
        skipPreviouslyVisitedBlocks: Boolean = false,
    ): List<BlockNode> {
        if (depth > maxDepth) {
            throw SourceMappingException("Notion block nesting exceeds the configured maximum depth")
        }
        if (!state.collectedChildrenParentIds.add(blockIdentity(parentBlockId))) {
            return emptyList()
        }
        checkDeadline(startedAt)
        val blocks = client.fetchDirectBlockChildren(parentBlockId)
        checkDeadline(startedAt)
        val activeBlocks = blocks.filterNot(NotionBlockEnvelope::inTrash)
        activeBlocks.mapNotNullTo(state.transcriptBlockIds) { block -> meetingNotesSections(block).transcriptBlockId }
        return activeBlocks.asSequence()
            .filterNot { block -> blockIdentity(block.id) in state.transcriptBlockIds }
            .filterNot { block -> skipPreviouslyVisitedBlocks && blockIdentity(block.id) in state.visitedBlockIds }
            .map { block ->
                countAndVisit(block, state)
                val ordinaryChildren = if (block.hasChildren && block.type != CHILD_PAGE_TYPE) {
                    collectChildren(
                        block.id,
                        depth + 1,
                        block.type,
                        sourceDocument,
                        startedAt,
                        state,
                        skipPreviouslyVisitedBlocks,
                    )
                } else {
                    emptyList()
                }
                val meetingSections = meetingNotesSections(block)
                val sectionChildren = meetingSections.publicBlockIds
                    .asSequence()
                    .filterNot { sectionBlockId -> blockIdentity(sectionBlockId) in state.transcriptBlockIds }
                    .flatMap { sectionBlockId ->
                        collectChildren(
                            sectionBlockId,
                            depth + 1,
                            block.type,
                            sourceDocument,
                            startedAt,
                            state,
                            skipPreviouslyVisitedBlocks = true,
                        ).asSequence()
                    }
                    .toList()
                val children = ordinaryChildren + sectionChildren
                val mapped = if (parentType == TAB_TYPE && block.type == PARAGRAPH_TYPE) {
                    blockMapper.mapTabItem(block, children)
                } else {
                    blockMapper.map(block, children, sourceDocument)
                }
                if (block.type == CHILD_PAGE_TYPE) {
                    confirmContainedChild(block, sourceDocument, startedAt, state)
                }
                mapped
            }
            .toList()
    }

    private fun meetingNotesSections(block: NotionBlockEnvelope): MeetingNotesSections {
        if (block.type != MEETING_NOTES_TYPE) {
            return MeetingNotesSections.EMPTY
        }
        val children = block.payload.get("children") ?: return MeetingNotesSections.EMPTY
        if (children.isNull) {
            return MeetingNotesSections.EMPTY
        }
        if (!children.isObject) {
            throw SourceMappingException("Notion meeting notes children must be an object or null")
        }
        val transcriptBlockId = children.optionalBlockId("transcript_block_id")
        return MeetingNotesSections(
            publicBlockIds = listOfNotNull(
                children.optionalBlockId("summary_block_id"),
                children.optionalBlockId("notes_block_id"),
            ).distinct(),
            transcriptBlockId = transcriptBlockId,
        )
    }

    private fun JsonNode.optionalBlockId(field: String): String? {
        val value = get(field) ?: return null
        if (value.isNull) {
            return null
        }
        if (!value.isString || value.stringValue().isBlank()) {
            throw SourceMappingException("Notion meeting notes $field must be a nonblank block ID or null")
        }
        return try {
            NotionIdNormalizer.normalize(value.stringValue())
        } catch (exception: SourceMappingException) {
            throw SourceMappingException("Notion meeting notes $field is malformed", exception)
        }
    }

    private fun blockIdentity(value: String): String = try {
        NotionIdNormalizer.normalize(value)
    } catch (_: SourceMappingException) {
        value
    }

    private fun countAndVisit(block: NotionBlockEnvelope, state: TraversalState) {
        if (!state.visitedBlockIds.add(blockIdentity(block.id))) {
            throw SourceMappingException("Notion block collection contains a cycle or duplicate block")
        }
        state.blockCount += 1
        if (state.blockCount > maxBlockCount) {
            throw SourceMappingException("Notion block collection exceeds the configured maximum count")
        }
    }

    private fun confirmContainedChild(
        block: NotionBlockEnvelope,
        sourceDocument: SourceDocumentRef,
        startedAt: Long,
        state: TraversalState,
    ) {
        val childPageId = NotionIdNormalizer.normalize(block.id)
        if (!state.visitedPageIds.add(childPageId)) {
            throw SourceMappingException("Notion child page collection contains a cycle or duplicate page")
        }
        checkDeadline(startedAt)
        val childPage = client.fetchPage(childPageId)
        checkDeadline(startedAt)
        if (NotionIdNormalizer.normalize(childPage.id) != childPageId) {
            throw SourceMappingException("Notion child page did not match the child page block")
        }
        if (childPage.parent.type == PAGE_PARENT_TYPE) {
            val parentPageId = childPage.parent.pageId
                ?.let(NotionIdNormalizer::normalize)
                ?: throw SourceMappingException("Notion child page parent is malformed")
            if (parentPageId == sourceDocument.externalId) {
                state.containedChildren += SourceDocumentRef(sourceId, childPageId)
            }
        }
    }

    private fun checkDeadline(startedAt: Long) {
        if (nanoTime() - startedAt >= collectionTimeout.toNanos()) {
            throw RetryableSourceException("Notion post collection deadline exceeded")
        }
    }

    private class TraversalState(
        val visitedPageIds: MutableSet<String>,
        val visitedBlockIds: MutableSet<String> = mutableSetOf(),
        val collectedChildrenParentIds: MutableSet<String> = mutableSetOf(),
        val transcriptBlockIds: MutableSet<String> = mutableSetOf(),
        val containedChildren: MutableList<SourceDocumentRef> = mutableListOf(),
        var blockCount: Int = 0,
    )

    private data class MeetingNotesSections(
        val publicBlockIds: List<String>,
        val transcriptBlockId: String?,
    ) {
        companion object {
            val EMPTY = MeetingNotesSections(emptyList(), null)
        }
    }

    private companion object {
        const val CHILD_PAGE_TYPE = "child_page"
        const val MEETING_NOTES_TYPE = "meeting_notes"
        const val PAGE_PARENT_TYPE = "page_id"
        const val PARAGRAPH_TYPE = "paragraph"
        const val TAB_TYPE = "tab"
    }
}
