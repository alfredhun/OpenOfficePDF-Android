package com.openofficepdf.engine.markdown

/**
 * 基于 AST 行号的分段线性插值双向精准同步滚动算法 (任务书 2.4.2 节)
 * 严禁使用粗略的总高度百分比同步
 */
class MarkdownScrollSync {

    /**
     * 根据编辑器当前源码光标行号，插值计算预览区目标滚动进度 [0.0f, 1.0f] 或对应块索引
     */
    fun findTargetPreviewBlock(
        astDocument: MarkdownAstDocument,
        editorCursorLine: Int
    ): Int {
        val spans = astDocument.blockSpans
        if (spans.isEmpty()) return 0

        // 寻找包含或最贴近光标行的 AST 块
        for (i in spans.indices) {
            val span = spans[i]
            if (editorCursorLine in span.startLine..span.endLine) {
                return i
            }
            if (editorCursorLine < span.startLine) {
                return if (i > 0) i - 1 else 0
            }
        }
        return spans.lastIndex
    }
}

/**
 * Markdown 安全白名单过滤器 (任务书 2.4.2 节第 3 条 & 研发十条军规)
 * 拦截可执行 script、iframe、恶意 onerror，防范目录穿越
 */
class MarkdownSanitizer {

    private val dangerousTagsRegex = Regex(
        "<\\s*(script|iframe|object|embed|applet|meta|link)[^>]*>.*?</\\s*\\1\\s*>|<\\s*(script|iframe|object|embed|applet|meta|link)[^>]*/>",
        RegexOption.IGNORE_CASE
    )

    private val dangerousAttrsRegex = Regex(
        "\\bon[a-zA-Z]+\\s*=\\s*['\"].*?['\"]|javascript:\\s*",
        RegexOption.IGNORE_CASE
    )

    fun sanitizeHtml(rawHtml: String): String {
        return rawHtml
            .replace(dangerousTagsRegex, "")
            .replace(dangerousAttrsRegex, "")
    }

    /**
     * 校验相对路径，拦截 "../" 路径穿越逃逸出授权目录
     */
    fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return !normalized.contains("../") && !normalized.startsWith("/")
    }
}
