package com.openofficepdf.engine.markdown

import com.openofficepdf.core.common.EngineResult
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

data class MarkdownBlockSpan(
    val nodeType: String,
    val startLine: Int,
    val endLine: Int,
    val sourceIndex: Int
)

data class MarkdownAstDocument(
    val rawText: String,
    val rootNode: Node,
    val blockSpans: List<MarkdownBlockSpan>,
    val headings: List<MarkdownHeadingItem>
)

data class MarkdownHeadingItem(
    val level: Int,
    val title: String,
    val sourceLine: Int
)

/**
 * 具有源码行号追踪能力的 Markdown AST 解析器 (任务书 2.4.2 节 & 8.2.5 节)
 */
class MarkdownAstParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), TaskListItemsExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES) // 强制注入精确行号
        .build()

    fun parse(markdownContent: String): EngineResult<MarkdownAstDocument> {
        return try {
            val documentNode = parser.parse(markdownContent)
            val spans = mutableListOf<MarkdownBlockSpan>()
            val headings = mutableListOf<MarkdownHeadingItem>()

            var child = documentNode.firstChild
            var index = 0
            while (child != null) {
                val spanList = child.sourceSpans
                if (spanList != null && spanList.isNotEmpty()) {
                    val firstSpan = spanList.first()
                    val lastSpan = spanList.last()
                    val startLine = firstSpan.lineIndex + 1
                    val endLine = lastSpan.lineIndex + 1

                    spans.add(
                        MarkdownBlockSpan(
                            nodeType = child.javaClass.simpleName,
                            startLine = startLine,
                            endLine = endLine,
                            sourceIndex = index
                        )
                    )

                    if (child is Heading) {
                        val headingText = extractText(child)
                        headings.add(
                            MarkdownHeadingItem(
                                level = child.level,
                                title = headingText,
                                sourceLine = startLine
                            )
                        )
                    }
                }
                child = child.next
                index++
            }

            EngineResult.Success(
                MarkdownAstDocument(
                    rawText = markdownContent,
                    rootNode = documentNode,
                    blockSpans = spans,
                    headings = headings
                )
            )
        } catch (e: Exception) {
            EngineResult.Failure(
                code = com.openofficepdf.core.common.EngineErrorCode.UNSUPPORTED_OBJECT_STRUCTURE,
                message = "Failed to parse markdown AST: ${e.message}",
                cause = e
            )
        }
    }

    private fun extractText(node: Node): String {
        val sb = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                sb.append(text.literal)
            }
            override fun visit(code: Code) {
                sb.append(code.literal)
            }
        })
        return sb.toString()
    }
}
