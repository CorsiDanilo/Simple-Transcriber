package com.anomalyzed.simpletranscriber.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val INLINE_MARKDOWN_REGEX = Regex(
    """(\*\*\*(.+?)\*\*\*|___(.+?)___|\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|~~(.+?)~~|\*(.+?)\*|_(.+?)_)"""
)

fun parseMarkdown(text: String, defaultFontSize: TextUnit = 14.sp): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        val lines = text.lines()
        for ((index, line) in lines.withIndex()) {
            var currentLine = line.trimEnd('\r', ' ')
            var lineFontSize = defaultFontSize
            var lineFontWeight = FontWeight.Normal

            // Intestazioni (Headers)
            when {
                currentLine.startsWith("### ") -> {
                    lineFontSize = (defaultFontSize.value + 1).sp
                    lineFontWeight = FontWeight.Bold
                    currentLine = currentLine.removePrefix("### ")
                }
                currentLine.startsWith("## ") -> {
                    lineFontSize = (defaultFontSize.value + 2).sp
                    lineFontWeight = FontWeight.Bold
                    currentLine = currentLine.removePrefix("## ")
                }
                currentLine.startsWith("# ") -> {
                    lineFontSize = (defaultFontSize.value + 4).sp
                    lineFontWeight = FontWeight.Bold
                    currentLine = currentLine.removePrefix("# ")
                }
                // Liste puntate (Bullets)
                currentLine.startsWith("  - ") || currentLine.startsWith("  * ") -> {
                    currentLine = "    • " + currentLine.substring(4)
                }
                currentLine.startsWith("- ") || currentLine.startsWith("* ") || currentLine.startsWith("+ ") -> {
                    currentLine = "• " + currentLine.substring(2)
                }
                currentLine.trim() == "---" || currentLine.trim() == "***" -> {
                    currentLine = "──────────"
                }
            }

            appendInlineStyledText(
                rawText = currentLine,
                baseFontSize = lineFontSize,
                baseFontWeight = lineFontWeight
            )

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineStyledText(
    rawText: String,
    baseFontSize: TextUnit,
    baseFontWeight: FontWeight
) {
    var lastIndex = 0
    for (match in INLINE_MARKDOWN_REGEX.findAll(rawText)) {
        val range = match.range
        if (range.first > lastIndex) {
            withStyle(SpanStyle(fontSize = baseFontSize, fontWeight = baseFontWeight)) {
                append(rawText.substring(lastIndex, range.first))
            }
        }

        val boldItalic = match.groups[2]?.value ?: match.groups[3]?.value
        val bold = match.groups[4]?.value ?: match.groups[5]?.value
        val code = match.groups[6]?.value
        val strike = match.groups[7]?.value
        val italic = match.groups[8]?.value ?: match.groups[9]?.value

        when {
            boldItalic != null -> {
                withStyle(SpanStyle(fontSize = baseFontSize, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(boldItalic)
                }
            }
            bold != null -> {
                withStyle(SpanStyle(fontSize = baseFontSize, fontWeight = FontWeight.Bold)) {
                    append(bold)
                }
            }
            italic != null -> {
                withStyle(SpanStyle(fontSize = baseFontSize, fontWeight = baseFontWeight, fontStyle = FontStyle.Italic)) {
                    append(italic)
                }
            }
            code != null -> {
                withStyle(SpanStyle(fontSize = baseFontSize, fontFamily = FontFamily.Monospace, background = Color(0x22888888))) {
                    append(code)
                }
            }
            strike != null -> {
                withStyle(SpanStyle(fontSize = baseFontSize, textDecoration = TextDecoration.LineThrough)) {
                    append(strike)
                }
            }
        }

        lastIndex = range.last + 1
    }

    if (lastIndex < rawText.length) {
        withStyle(SpanStyle(fontSize = baseFontSize, fontWeight = baseFontWeight)) {
            append(rawText.substring(lastIndex))
        }
    }
}

