package com.anomalyzed.simpletranscriber.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for ((index, line) in lines.withIndex()) {
            var currentLine = line.trimEnd('\r', ' ')
            var isHeader = false
            var fontSize = 14.sp
            var fontWeight = FontWeight.Normal

            // Intestazioni
            if (currentLine.startsWith("### ")) {
                isHeader = true
                fontSize = 16.sp
                fontWeight = FontWeight.Bold
                currentLine = currentLine.removePrefix("### ")
            } else if (currentLine.startsWith("## ")) {
                isHeader = true
                fontSize = 18.sp
                fontWeight = FontWeight.Bold
                currentLine = currentLine.removePrefix("## ")
            } else if (currentLine.startsWith("# ")) {
                isHeader = true
                fontSize = 20.sp
                fontWeight = FontWeight.Bold
                currentLine = currentLine.removePrefix("# ")
            }

            // Liste puntate
            if (currentLine.startsWith("- ") || currentLine.startsWith("* ")) {
                currentLine = "• " + currentLine.substring(2)
            }

            // Rimuoviamo i tag markdown di grassetto/corsivo per pulire il testo
            // In un parser completo qui applicheremmo gli stili interni
            currentLine = currentLine.replace("**", "")
            currentLine = currentLine.replace("__", "")

            withStyle(SpanStyle(fontSize = fontSize, fontWeight = fontWeight)) {
                append(currentLine)
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}
