package com.openofficepdf.engine.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * 递归/单遍状态机行内标记解析器 (Inline Span Parser)
 * 彻底消除裸露的 `*`、`**`、`***`、`~~`、``` 以及 `$` 符号
 */
object MarkdownInlineParser {

    /**
     * 将带有 Markdown 行内标记的原始字符串转换为 Compose 的 AnnotatedString
     * @param raw 原始 markdown 文本行
     * @param isNightMode 是否夜间模式，影响行内代码与公式的高亮背景色
     */
    fun parseInlineSpans(raw: String, isNightMode: Boolean = false): AnnotatedString {
        if (raw.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            var i = 0
            val len = raw.length

            while (i < len) {
                val c = raw[i]

                // 1. 转义字符支持：\*、\$、\` 等
                if (c == '\\' && i + 1 < len) {
                    append(raw[i + 1])
                    i += 2
                    continue
                }

                // 2. 行内代码：`code`
                if (c == '`') {
                    val endIdx = raw.indexOf('`', i + 1)
                    if (endIdx != -1) {
                        val codeText = raw.substring(i + 1, endIdx)
                        val codeBg = if (isNightMode) Color(0xFF2D3748) else Color(0xFFF1F5F9)
                        val codeColor = if (isNightMode) Color(0xFFF687B3) else Color(0xFFE11D48)
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = codeColor,
                                background = codeBg,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        append(" $codeText ")
                        pop()
                        i = endIdx + 1
                        continue
                    }
                }

                // 3. 行内数学公式：$formula$ (非 $$ 块级公式)
                if (c == '$' && (i + 1 >= len || raw[i + 1] != '$')) {
                    val endIdx = findMatchingDollar(raw, i + 1)
                    if (endIdx != -1) {
                        val latex = raw.substring(i + 1, endIdx).trim()
                        val mathBg = if (isNightMode) Color(0xFF1E293B) else Color(0xFFF0FDF4)
                        val mathColor = if (isNightMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        val prettyMath = formatMathSnippet(latex)

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
                        i = endIdx + 1
                        continue
                    }
                }

                // 4. 粗斜体叠加：***text*** 或 ___text___
                if ((raw.startsWith("***", i) || raw.startsWith("___", i)) && i + 3 < len) {
                    val delimiter = raw.substring(i, i + 3)
                    val endIdx = raw.indexOf(delimiter, i + 3)
                    if (endIdx != -1) {
                        val text = raw.substring(i + 3, endIdx)
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        append(text)
                        pop()
                        i = endIdx + 3
                        continue
                    }
                }

                // 5. 粗体：**text** 或 __text__
                if ((raw.startsWith("**", i) || raw.startsWith("__", i)) && i + 2 < len) {
                    val delimiter = raw.substring(i, i + 2)
                    val endIdx = raw.indexOf(delimiter, i + 2)
                    if (endIdx != -1) {
                        val text = raw.substring(i + 2, endIdx)
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(text)
                        pop()
                        i = endIdx + 2
                        continue
                    }
                }

                // 6. 斜体：*text* 或 _text_
                if ((c == '*' || c == '_') && i + 1 < len) {
                    val endIdx = raw.indexOf(c, i + 1)
                    if (endIdx != -1 && endIdx > i + 1 && !raw[i + 1].isWhitespace()) {
                        val text = raw.substring(i + 1, endIdx)
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text)
                        pop()
                        i = endIdx + 1
                        continue
                    }
                }

                // 7. 删除线：~~text~~
                if (raw.startsWith("~~", i) && i + 2 < len) {
                    val endIdx = raw.indexOf("~~", i + 2)
                    if (endIdx != -1) {
                        val text = raw.substring(i + 2, endIdx)
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(text)
                        pop()
                        i = endIdx + 2
                        continue
                    }
                }

                // 8. 链接：[label](url)
                if (c == '[') {
                    val closeBracket = raw.indexOf(']', i + 1)
                    if (closeBracket != -1 && closeBracket + 1 < len && raw[closeBracket + 1] == '(') {
                        val closeParen = raw.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val label = raw.substring(i + 1, closeBracket)
                            val linkColor = if (isNightMode) Color(0xFF60A5FA) else Color(0xFF2563EB)
                            pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(label)
                            pop()
                            i = closeParen + 1
                            continue
                        }
                    }
                }

                // 普通字符原样写入
                append(c)
                i++
            }
        }
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
     * 将常见简单的 LaTeX 字符符号美化转换为 Unicode 视觉字符 (100% 本地极速转换)
     * 例如 \alpha -> α, \beta -> β, \times -> ×, \approx -> ≈, \le -> ≤, \ge -> ≥, \frac{a}{b} -> (a/b)
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
            .replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}")) { match ->
                "(${match.groupValues[1]} / ${match.groupValues[2]})"
            }
            .replace(Regex("\\^([0-9a-zA-Z])")) { match ->
                toSuperscript(match.groupValues[1])
            }
            .replace(Regex("_([0-9a-zA-Z])")) { match ->
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
            "n" -> "ⁿ"; "x" -> "ˣ"; "i" -> "ⁱ"
            else -> "^$ch"
        }
    }

    private fun toSubscript(ch: String): String {
        return when (ch) {
            "0" -> "₀"; "1" -> "₁"; "2" -> "₂"; "3" -> "₃"; "4" -> "₄"
            "5" -> "₅"; "6" -> "₆"; "7" -> "₇"; "8" -> "₈"; "9" -> "₉"
            "+", "-" -> if (ch == "+") "₊" else "₋"
            "i" -> "ᵢ"; "j" -> "ⱼ"; "n" -> "ₙ"; "x" -> "ₓ"
            else -> "_$ch"
        }
    }
}
