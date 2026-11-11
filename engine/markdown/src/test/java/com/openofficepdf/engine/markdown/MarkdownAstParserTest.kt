package com.openofficepdf.engine.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownAstParserTest {

    private val parser = MarkdownAstParser()
    private val sanitizer = MarkdownSanitizer()

    @Test
    fun testParseBlockSpansAndHeadings() {
        val markdown = """
            # Document Title
            
            This is a paragraph.
            
            ## Section 1
            
            - Item A
            - Item B
        """.trimIndent()

        val result = parser.parse(markdown)
        assertTrue(result.isSuccess)
        val ast = result.getOrThrow()

        // 验证标题提取
        assertEquals(2, ast.headings.size)
        assertEquals("Document Title", ast.headings[0].title)
        assertEquals(1, ast.headings[0].level)
        assertEquals("Section 1", ast.headings[1].title)
        assertEquals(2, ast.headings[1].level)

        // 验证注入了源码行号
        assertTrue(ast.blockSpans.isNotEmpty())
        assertEquals(1, ast.blockSpans[0].startLine)
    }

    @Test
    fun testSanitizerStripsDangerousHtml() {
        val maliciousHtml = "<p>Normal</p><script>alert('XSS')</script><iframe src='evil.com'></iframe><img src='x' onerror='hack()'/>"
        val clean = sanitizer.sanitizeHtml(maliciousHtml)

        assertFalse(clean.contains("<script>"))
        assertFalse(clean.contains("<iframe>"))
        assertFalse(clean.contains("onerror"))
        assertTrue(clean.contains("<p>Normal</p>"))
    }

    @Test
    fun testSanitizerRejectsPathTraversal() {
        assertTrue(sanitizer.isSafeRelativePath("images/photo.png"))
        assertTrue(sanitizer.isSafeRelativePath("./assets/diagram.svg"))
        assertFalse(sanitizer.isSafeRelativePath("../private_db.realm"))
        assertFalse(sanitizer.isSafeRelativePath("/etc/passwd"))
    }
}
