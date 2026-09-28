package com.tobicodes.mute.core.util

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

/**
 * Extracts text with coordinate awareness:
 * detects two-column layouts and reorders into natural reading order
 * (left column top-to-bottom, then right column).
 */
class StructuredStripper : PDFTextStripper() {

    private data class Line(val y: Float, val x: Float, val text: String)

    private val pageLines = mutableListOf<MutableList<Line>>()
    private var current: MutableList<Line>? = null

    init {
        sortByPosition = true
    }

    val extractedPages: List<String> get() = pageLines.map { assemble(it) }

    override fun processPage(page: PDPage) {
        current = mutableListOf()
        pageLines.add(current!!)
        super.processPage(page)
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        val first = textPositions.firstOrNull() ?: return
        current?.add(Line(first.yDirAdj, first.xDirAdj, text))
        // Note: super NOT called — we assemble text ourselves from coordinates
    }

    private fun assemble(lines: List<Line>): String {
        if (lines.isEmpty()) return ""

        // Group into visual rows by Y coordinate
        val sorted = lines.sortedWith(compareBy({ it.y }, { it.x }))
        val rows = mutableListOf<MutableList<Line>>()
        for (l in sorted) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().y - l.y) < 3f) row.add(l)
            else rows.add(mutableListOf(l))
        }

        val rowStartX = rows.map { r -> r.minOf { it.x } }
        val mid = findColumnGap(rowStartX)

        val ordered = if (mid == null) {
            rows
        } else {
            rows.filter { r -> r.minOf { it.x } < mid } +
            rows.filter { r -> r.minOf { it.x } >= mid }
        }

        return ordered.joinToString("\n") { r ->
            r.sortedBy { it.x }.joinToString(" ") { it.text.trim() }
                .replace(Regex("\\s+"), " ").trim()
        }
    }

    /** Detects a bimodal gap in line start positions = two columns */
    private fun findColumnGap(xs: List<Float>): Float? {
        if (xs.size < 8) return null
        var bestGap = 0f
        var bestAt: Float? = null
        for (i in 1 until xs.size) {
            val gap = xs[i] - xs[i - 1]
            if (gap > bestGap) {
                bestGap = gap
                bestAt = (xs[i] + xs[i - 1]) / 2
            }
        }
        val span = xs.last() - xs.first()
        return if (bestGap > 40f && bestGap > span * 0.15f) bestAt else null
    }
}
