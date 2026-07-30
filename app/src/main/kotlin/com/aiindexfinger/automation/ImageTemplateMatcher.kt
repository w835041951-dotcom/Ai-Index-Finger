package com.aiindexfinger.automation

import kotlin.math.abs
import kotlin.math.max

data class LumaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xff
}

sealed interface TemplateMatchResult {
    data class Unique(
        val centerX: Int,
        val centerY: Int,
        val scorePermille: Int,
    ) : TemplateMatchResult

    data object NoMatch : TemplateMatchResult
    data object Ambiguous : TemplateMatchResult
}

fun matchTemplate(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    ambiguityMarginPermille: Int,
    checkCancellation: () -> Unit = {},
): TemplateMatchResult {
    if (template.width > screen.width || template.height > screen.height) return TemplateMatchResult.NoMatch
    if (templateVariance(template) < MIN_TEMPLATE_VARIANCE) return TemplateMatchResult.NoMatch

    val coarseStride = max(2, minOf(template.width, template.height) / 6)
    val coarseCandidates = mutableListOf<ScoredPosition>()
    for (top in 0..screen.height - template.height step coarseStride) {
        checkCancellation()
        for (left in 0..screen.width - template.width step coarseStride) {
            retainBest(
                coarseCandidates,
                ScoredPosition(
                    left,
                    top,
                    similarity(screen, template, left, top, MAX_COARSE_SAMPLES, checkCancellation),
                ),
                MAX_COARSE_CANDIDATES,
            )
        }
    }

    val refined = mutableListOf<ScoredPosition>()
    coarseCandidates.forEach { candidate ->
        val startX = (candidate.left - coarseStride).coerceAtLeast(0)
        val endX = (candidate.left + coarseStride).coerceAtMost(screen.width - template.width)
        val startY = (candidate.top - coarseStride).coerceAtLeast(0)
        val endY = (candidate.top + coarseStride).coerceAtMost(screen.height - template.height)
        for (top in startY..endY) {
            checkCancellation()
            for (left in startX..endX) {
                retainBest(
                    refined,
                    ScoredPosition(
                        left,
                        top,
                        similarity(screen, template, left, top, MAX_FINE_SAMPLES, checkCancellation),
                    ),
                    MAX_REFINED_CANDIDATES,
                )
            }
        }
    }

    val distinct = refined
        .sortedByDescending { it.scorePermille }
        .fold(mutableListOf<ScoredPosition>()) { accepted, candidate ->
            val separated = accepted.all {
                abs(it.left - candidate.left) >= template.width / 2 ||
                    abs(it.top - candidate.top) >= template.height / 2
            }
            if (separated) accepted += candidate
            accepted
        }
    val best = distinct.firstOrNull() ?: return TemplateMatchResult.NoMatch
    if (best.scorePermille < minimumScorePermille) return TemplateMatchResult.NoMatch
    val second = distinct.getOrNull(1)
    if (second != null && second.scorePermille >= minimumScorePermille &&
        best.scorePermille - second.scorePermille < ambiguityMarginPermille
    ) {
        return TemplateMatchResult.Ambiguous
    }
    return TemplateMatchResult.Unique(
        centerX = best.left + template.width / 2,
        centerY = best.top + template.height / 2,
        scorePermille = best.scorePermille,
    )
}

private fun similarity(
    screen: LumaImage,
    template: LumaImage,
    left: Int,
    top: Int,
    maxSamples: Int,
    checkCancellation: () -> Unit,
): Int {
    val sampleStride = max(1, kotlin.math.sqrt((template.width * template.height / maxSamples).toDouble()).toInt())
    var totalDifference = 0L
    var count = 0
    for (y in 0 until template.height step sampleStride) {
        checkCancellation()
        for (x in 0 until template.width step sampleStride) {
            totalDifference += abs(screen[left + x, top + y] - template[x, y])
            count++
        }
    }
    return (1_000L - totalDifference * 1_000L / (count * 255L)).toInt().coerceIn(0, 1_000)
}

private fun templateVariance(template: LumaImage): Int {
    val average = template.pixels.sumOf { it.toInt() and 0xff } / template.pixels.size
    return template.pixels.sumOf { abs((it.toInt() and 0xff) - average) } / template.pixels.size
}

private fun retainBest(items: MutableList<ScoredPosition>, candidate: ScoredPosition, limit: Int) {
    items += candidate
    if (items.size > limit) items.removeAt(items.indices.minBy { items[it].scorePermille })
}

private data class ScoredPosition(val left: Int, val top: Int, val scorePermille: Int)

private const val MIN_TEMPLATE_VARIANCE = 4
private const val MAX_COARSE_SAMPLES = 64
private const val MAX_FINE_SAMPLES = 512
private const val MAX_COARSE_CANDIDATES = 12
private const val MAX_REFINED_CANDIDATES = 24
