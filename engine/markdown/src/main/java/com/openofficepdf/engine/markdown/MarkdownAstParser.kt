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
 * 具备健壮的预处理与符号剥离：
 * - 剥离多行或单行数学公式 $$...$$ 标识符
 * - 清洗标题前缀 #、引用符 >、列表标记 -, *, +
 * - 清洗表格分隔线并准确规整单元格文本
 */
class MarkdownAstParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), TaskListItemsExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build()

    fun parseToBlocks(markdownContent: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdownContent.lines()
        var inCodeBlock = false
        var codeLang: String? = null
        val codeBuffer = StringBuilder()
        var codeStartLine = 0

        var inMathBlock = false
        val mathBuffer = StringBuilder()
        var mathStartLine = 0

        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            val trimmed = line.trim()

            // 1. 处理代码块围栏 ```
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    val code = codeBuffer.toString().trimEnd()
                    if (codeLang?.equals("mermaid", ignoreCase = true) == true) {
                        blocks.add(MarkdownBlock.MermaidChart(code, codeStartLine))
                    } else {
                        blocks.add(MarkdownBlock.CodeBlock(code, codeLang, codeStartLine))
                    }
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim().ifEmpty { null }
                    codeStartLine = lineNum
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                continue
            }

            // 2. 处理跨行独立数学公式块 $$ ... $$
            if (trimmed == "$$") {
                if (inMathBlock) {
                    val formula = mathBuffer.toString().trim()
                    blocks.add(MarkdownBlock.MathFormula(formula, mathStartLine))
                    mathBuffer.clear()
                    inMathBlock = false
                } else {
                    inMathBlock = true
                    mathStartLine = lineNum
                }
                continue
            }

            if (inMathBlock) {
                mathBuffer.append(line).append(" ")
                continue
            }

            // 3. 处理单行独立数学公式块 $$formula$$
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length >= 4) {
                val formula = trimmed.substring(2, trimmed.length - 2).trim()
                blocks.add(MarkdownBlock.MathFormula(formula, lineNum))
                continue
            }

            // 4. 处理标题 # ... ######
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val title = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Heading(level, title, lineNum))
                continue
            }

            // 5. 处理块引用 >
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                blocks.add(MarkdownBlock.BlockQuote(quoteText, lineNum))
                continue
            }

            // 6. 处理任务列表 - [ ] / - [x]
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ")) {
                blocks.add(MarkdownBlock.TaskListItem(trimmed.drop(6).trim(), isChecked = false, depth = 0, lineIndex = lineNum))
                continue
            }
            if (trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true)) {
                blocks.add(MarkdownBlock.TaskListItem(trimmed.drop(6).trim(), isChecked = true, depth = 0, lineIndex = lineNum))
                continue
            }

            // 7. 处理无序与有序列表项
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                blocks.add(MarkdownBlock.ListItem(trimmed.drop(2).trim(), depth = 0, lineIndex = lineNum))
                continue
            }
            val orderedMatch = Regex("""^\d+\.\s+(.*)""").find(trimmed)
            if (orderedMatch != null) {
                val itemText = orderedMatch.groupValues[1].trim()
                blocks.add(MarkdownBlock.ListItem(itemText, depth = 0, lineIndex = lineNum))
                continue
            }

            // 8. 处理表格行 | col1 | col2 |
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // 忽略分割行 | --- | --- |
                if (!trimmed.contains("---") && !trimmed.contains(":---")) {
                    val rawCells = trimmed.split("|")
                    // 剥离最外侧的两个空元素
                    val cells = rawCells.subList(1, rawCells.lastIndex).map { it.trim() }
                    blocks.add(MarkdownBlock.Table(listOf(cells), lineNum))
                }
                continue
            }

            // 9. 处理普通文本段落
            if (trimmed.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(line, lineNum))
            }
        }

        // 处理未闭合的代码块或公式块保底
        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd(), codeLang, codeStartLine))
        }
        if (inMathBlock && mathBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.MathFormula(mathBuffer.toString().trim(), mathStartLine))
        }

        return blocks
    }

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
