package com.openofficepdf.feature.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * 结构化行内标记解析与符号剥离引擎 (Inline Tokenizer & Styler)
 * 参考成熟 Markdown 解析器设计：
 * 1. 优先提取隔离原子令牌：行内代码 `code`、LaTeX 数学公式 $...$ / $$...$$、图片标签 ![alt](url)、超链接 [text](url)
 * 2. 严谨解析并剥离粗体、斜体、粗斜体、删除线定界符 (***, **, *, ___, __, _, ~~)
 * 3. 彻底防止孤立定界符残留、符号裸露在阅读视图中
 * 4. 100% 本地离线执行，零网络开销
 */
object MarkdownInlineParser {

    private sealed class Token {
        data class Plain(val text: String) : Token()
        data class InlineCode(val code: String) : Token()
        data class InlineMath(val latex: String) : Token()
        data class BoldItalic(val innerText: String) : Token()
        data class Bold(val innerText: String) : Token()
        data class Italic(val innerText: String) : Token()
        data class Strikethrough(val innerText: String) : Token()
        data class Link(val text: String, val url: String) : Token()
        data class ImageTag(val alt: String, val url: String) : Token()
    }

    /**
     * 将包含 Markdown 行内标记的字符串解析转换为 Compose 原生 AnnotatedString
     */
    fun parseInlineSpans(raw: String, isNightMode: Boolean = false): AnnotatedString {
        if (raw.isEmpty()) return AnnotatedString("")

        // 第一步：清洗残留的块级标识符（如某些段落中残存的多余 '#' 或 '>'）
        val sanitized = sanitizeLinePrefixes(raw)

        // 第二步：执行状态机与模式扫描，分发为令牌序列
        val tokens = tokenize(sanitized)

        // 第三步：依据令牌生成 Compose AnnotatedString，并应用现代排版色彩
        return buildAnnotatedString {
            for (token in tokens) {
                when (token) {
                    is Token.Plain -> {
                        append(cleanRemainingSymbols(token.text))
                    }
                    is Token.InlineCode -> {
                        val codeBg = if (isNightMode) Color(0xFF2D3748) else Color(0xFFF1F5F9)
                        val codeColor = if (isNightMode) Color(0xFFF687B3) else Color(0xFFBE123C)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = codeColor,
                                background = codeBg,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(" ${token.code} ")
                        pop()
                    }
                    is Token.InlineMath -> {
                        val mathBg = if (isNightMode) Color(0xFF1E293B) else Color(0xFFF0FDF4)
                        val mathColor = if (isNightMode) Color(0xFF38BDF8) else Color(0xFF0F766E)
                        val prettyMath = formatMathSnippet(token.latex)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                color = mathColor,
                                background = mathBg,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(" $prettyMath ")
                        pop()
                    }
                    is Token.BoldItalic -> {
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        append(cleanRemainingSymbols(token.innerText))
                        pop()
                    }
                    is Token.Bold -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(cleanRemainingSymbols(token.innerText))
                        pop()
                    }
                    is Token.Italic -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(cleanRemainingSymbols(token.innerText))
                        pop()
                    }
                    is Token.Strikethrough -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(cleanRemainingSymbols(token.innerText))
                        pop()
                    }
                    is Token.Link -> {
                        val linkColor = if (isNightMode) Color(0xFF60A5FA) else Color(0xFF2563EB)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(token.text.ifEmpty { token.url })
                        pop()
                    }
                    is Token.ImageTag -> {
                        val imgBg = if (isNightMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                        val imgColor = if (isNightMode) Color(0xFF94A3B8) else Color(0xFF475569)
                        pushStyle(
                            SpanStyle(
                                background = imgBg,
                                color = imgColor,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(" [图片: ${token.alt.ifEmpty { "Image" }}] ")
                        pop()
                    }
                }
            }
        }
    }

    private fun sanitizeLinePrefixes(text: String): String {
        var s = text.trim()
        // 去除残留的多余标题井号（如 "### 标题" 被当成行内传入时）
        if (s.startsWith("#")) {
            val idx = s.indexOfFirst { it != '#' }
            if (idx != -1 && s.getOrNull(idx) == ' ') {
                s = s.substring(idx + 1).trim()
            }
        }
        // 去除残余的引用符
        if (s.startsWith(">")) {
            s = s.removePrefix(">").trim()
        }
        return s
    }

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            val c = text[cursor]

            // 1. 转义字符支持：\*、\$、\` 等
            if (c == '\\' && cursor + 1 < len) {
                tokens.add(Token.Plain(text[cursor + 1].toString()))
                cursor += 2
                continue
            }

            // 2. 行内代码：`code`
            if (c == '`') {
                val end = text.indexOf('`', cursor + 1)
                if (end != -1) {
                    val codeContent = text.substring(cursor + 1, end)
                    tokens.add(Token.InlineCode(codeContent))
                    cursor = end + 1
                    continue
                }
            }

            // 3. 数学公式：块级 $$...$$ 或行内 $...$
            if (c == '$') {
                if (cursor + 1 < len && text[cursor + 1] == '$') {
                    // $$ 块公式
                    val end = text.indexOf("$$", cursor + 2)
                    if (end != -1) {
                        val mathContent = text.substring(cursor + 2, end).trim()
                        tokens.add(Token.InlineMath(mathContent))
                        cursor = end + 2
                        continue
                    }
                } else {
                    // $ 行内公式
                    val end = findMatchingDollar(text, cursor + 1)
                    if (end != -1) {
                        val mathContent = text.substring(cursor + 1, end).trim()
                        tokens.add(Token.InlineMath(mathContent))
                        cursor = end + 1
                        continue
                    }
                }
            }

            // 4. 图片：![alt](url)
            if (c == '!' && cursor + 1 < len && text[cursor + 1] == '[') {
                val closeBracket = text.indexOf(']', cursor + 2)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val alt = text.substring(cursor + 2, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        tokens.add(Token.ImageTag(alt, url))
                        cursor = closeParen + 1
                        continue
                    }
                }
            }

            // 5. 链接：[label](url)
            if (c == '[') {
                val closeBracket = text.indexOf(']', cursor + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(cursor + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        tokens.add(Token.Link(label, url))
                        cursor = closeParen + 1
                        continue
                    }
                }
            }

            // 6. 粗斜体：***text*** 或 ___text___
            if ((text.startsWith("***", cursor) || text.startsWith("___", cursor)) && cursor + 3 < len) {
                val delim = text.substring(cursor, cursor + 3)
                val end = text.indexOf(delim, cursor + 3)
                if (end != -1) {
                    val inner = text.substring(cursor + 3, end)
                    tokens.add(Token.BoldItalic(inner))
                    cursor = end + 3
                    continue
                }
            }

            // 7. 粗体：**text** 或 __text__
            if ((text.startsWith("**", cursor) || text.startsWith("__", cursor)) && cursor + 2 < len) {
                val delim = text.substring(cursor, cursor + 2)
                val end = text.indexOf(delim, cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    tokens.add(Token.Bold(inner))
                    cursor = end + 2
                    continue
                }
            }

            // 8. 删除线：~~text~~
            if (text.startsWith("~~", cursor) && cursor + 2 < len) {
                val end = text.indexOf("~~", cursor + 2)
                if (end != -1) {
                    val inner = text.substring(cursor + 2, end)
                    tokens.add(Token.Strikethrough(inner))
                    cursor = end + 2
                    continue
                }
            }

            // 9. 单斜体：*text* 或 _text_（要求闭合且不是孤立符号）
            if ((c == '*' || c == '_') && cursor + 1 < len) {
                val end = text.indexOf(c, cursor + 1)
                if (end != -1 && end > cursor + 1 && !text[cursor + 1].isWhitespace()) {
                    val inner = text.substring(cursor + 1, end)
                    // 确保内部没有未闭合的同类符号破坏层级
                    if (!inner.contains(c)) {
                        tokens.add(Token.Italic(inner))
                        cursor = end + 1
                        continue
                    }
                }
            }

            // 普通字符收集到 Plain 令牌
            val lastToken = tokens.lastOrNull()
            if (lastToken is Token.Plain) {
                tokens[tokens.lastIndex] = Token.Plain(lastToken.text + c)
            } else {
                tokens.add(Token.Plain(c.toString()))
            }
            cursor++
        }

        return tokens
    }

    /**
     * 清理所有残留无用的 Markdown 孤立定界符号
     * 例如漏网的孤立 `*`、`**`、`_`、`~`、`$`，防止泄漏给用户
     */
    private fun cleanRemainingSymbols(text: String): String {
        return text
            .replace(Regex("""^\*+\s|\s\*+$"""), " ")
            .replace(Regex("""^~+\s|\s~+$"""), " ")
            .replace(Regex("""(?<=\s)\*(?=\s)"""), "")
            .replace(Regex("""(?<=\s)\$\$(?=\s)"""), "")
            .replace(Regex("""(?<=\s)\$(?=\s)"""), "")
    }

    private fun findMatchingDollar(s: String, startIndex: Int): Int {
        var idx = startIndex
        while (idx < s.length) {
            if (s[idx] == '\\') {
                idx += 2
                continue
            }
            if (s[idx] == '$') return idx
            idx++
        }
        return -1
    }

    /**
     * 将常见 LaTeX 数学公式美化转换为 Unicode 视觉字符 (100% 本地极速转换)
     */
    fun formatMathSnippet(latex: String): String {
        return latex
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\delta", "δ")
            .replace("\\epsilon", "ε")
            .replace("\\theta", "θ")
            .replace("\\lambda", "λ")
            .replace("\\mu", "μ")
            .replace("\\pi", "π")
            .replace("\\sigma", "σ")
            .replace("\\tau", "τ")
            .replace("\\phi", "φ")
            .replace("\\omega", "ω")
            .replace("\\sum", "∑")
            .replace("\\prod", "∏")
            .replace("\\int", "∫")
            .replace("\\infty", "∞")
            .replace("\\approx", "≈")
            .replace("\\neq", "≠")
            .replace("\\le", "≤")
            .replace("\\ge", "≥")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\sqrt", "√")
            .replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")) { match ->
                "(${match.groupValues[1]} / ${match.groupValues[2]})"
            }
            .replace(Regex("""\^\{([^}]+)\}""")) { match ->
                match.groupValues[1].map { toSuperscript(it.toString()) }.joinToString("")
            }
            .replace(Regex("""\^([0-9a-zA-Z])""")) { match ->
                toSuperscript(match.groupValues[1])
            }
            .replace(Regex("""_\{([^}]+)\}""")) { match ->
                match.groupValues[1].map { toSubscript(it.toString()) }.joinToString("")
            }
            .replace(Regex("""_([0-9a-zA-Z])""")) { match ->
                toSubscript(match.groupValues[1])
            }
            .replace("\\{", "{")
            .replace("\\}", "}")
            .replace("\\", "")
            .trim()
    }

    private fun toSuperscript(ch: String): String {
        return when (ch) {
            "0" -> "⁰"; "1" -> "¹"; "2" -> "²"; "3" -> "³"; "4" -> "⁴"
            "5" -> "⁵"; "6" -> "⁶"; "7" -> "⁷"; "8" -> "⁸"; "9" -> "⁹"
            "+", "-" -> if (ch == "+") "⁺" else "⁻"
            "n" -> "ⁿ"; "x" -> "ˣ"; "i" -> "ⁱ"; "y" -> "ʸ"; "a" -> "ᵃ"; "b" -> "ᵇ"
            else -> ch
        }
    }

    private fun toSubscript(ch: String): String {
        return when (ch) {
            "0" -> "₀"; "1" -> "₁"; "2" -> "₂"; "3" -> "₃"; "4" -> "₄"
            "5" -> "₅"; "6" -> "₆"; "7" -> "₇"; "8" -> "₈"; "9" -> "₉"
            "+", "-" -> if (ch == "+") "₊" else "₋"
            "i" -> "ᵢ"; "j" -> "ⱼ"; "n" -> "ₙ"; "x" -> "ₓ"; "y" -> "ᵧ"; "k" -> "ₖ"
            else -> ch
        }
    }
}
