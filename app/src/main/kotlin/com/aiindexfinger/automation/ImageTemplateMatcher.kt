package com.aiindexfinger.automation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
    scaleTolerancePermille: Int = 0,
    checkCancellation: () -> Unit = {},
): TemplateMatchResult = matchTemplateInternal(
    screen,
    template,
    minimumScorePermille,
    ambiguityMarginPermille,
    scaleTolerancePermille,
    checkCancellation,
)

internal fun matchTemplateMeasured(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    ambiguityMarginPermille: Int,
    scaleTolerancePermille: Int = 0,
    checkCancellation: () -> Unit = {},
): TemplateMatchMeasurement {
    val work = TemplateMatchingWork()
    val result = matchTemplateInternal(
        screen,
        template,
        minimumScorePermille,
        ambiguityMarginPermille,
        scaleTolerancePermille,
        checkCancellation,
        work,
    )
    return TemplateMatchMeasurement(result, work.fineEvaluations)
}

private fun matchTemplateInternal(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    ambiguityMarginPermille: Int,
    scaleTolerancePermille: Int,
    checkCancellation: () -> Unit,
    work: TemplateMatchingWork? = null,
): TemplateMatchResult {
    require(scaleTolerancePermille in SUPPORTED_SCALE_TOLERANCES) { "Unsupported scale tolerance" }
    if (templateVariance(template) < MIN_TEMPLATE_VARIANCE) return TemplateMatchResult.NoMatch

    val refined = scalePermilles(scaleTolerancePermille).flatMap { scalePermille ->
        checkCancellation()
        findCandidates(screen, scaleLumaImage(template, scalePermille), checkCancellation, work)
    }
    val distinct = refined
        .sortedByDescending { it.scorePermille }
        .fold(mutableListOf<ScoredPosition>()) { accepted, candidate ->
            val separated = accepted.all { existing ->
                abs(existing.centerX - candidate.centerX) >= minOf(existing.width, candidate.width) / 2 ||
                    abs(existing.centerY - candidate.centerY) >= minOf(existing.height, candidate.height) / 2
            }
            if (separated) accepted += candidate
            accepted
        }
    val best = distinct.firstOrNull() ?: return TemplateMatchResult.NoMatch
    if (best.scorePermille < minimumScorePermille) return TemplateMatchResult.NoMatch
    val second = distinct.getOrNull(1)
    if (second != null && second.scorePermille >= minimumScorePermille &&
        (best.scorePermille == second.scorePermille ||
            best.scorePermille - second.scorePermille < ambiguityMarginPermille)
    ) {
        return TemplateMatchResult.Ambiguous
    }
    return TemplateMatchResult.Unique(
        centerX = best.centerX,
        centerY = best.centerY,
        scorePermille = best.scorePermille,
    )
}

private fun findCandidates(
    screen: LumaImage,
    template: LumaImage,
    checkCancellation: () -> Unit,
    work: TemplateMatchingWork?,
): List<ScoredPosition> {
    if (template.width > screen.width || template.height > screen.height) return emptyList()
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
                    template.width,
                    template.height,
                ),
                MAX_COARSE_CANDIDATES,
            )
        }
    }

    val refined = mutableListOf<ScoredPosition>()
    val refinedCoordinates = mutableSetOf<Long>()
    coarseCandidates.forEach { candidate ->
        val startX = (candidate.left - coarseStride).coerceAtLeast(0)
        val endX = (candidate.left + coarseStride).coerceAtMost(screen.width - template.width)
        val startY = (candidate.top - coarseStride).coerceAtLeast(0)
        val endY = (candidate.top + coarseStride).coerceAtMost(screen.height - template.height)
        for (top in startY..endY) {
            checkCancellation()
            for (left in startX..endX) {
                val coordinate = top.toLong() shl Int.SIZE_BITS or left.toLong()
                if (!refinedCoordinates.add(coordinate)) continue
                if (work != null) work.fineEvaluations++
                retainBest(
                    refined,
                    ScoredPosition(
                        left,
                        top,
                        similarity(screen, template, left, top, MAX_FINE_SAMPLES, checkCancellation),
                        template.width,
                        template.height,
                    ),
                    MAX_REFINED_CANDIDATES,
                )
            }
        }
    }
    return refined
}

internal data class TemplateMatchMeasurement(
    val result: TemplateMatchResult,
    val fineEvaluations: Int,
)

private class TemplateMatchingWork(var fineEvaluations: Int = 0)

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

internal fun scaleLumaImage(source: LumaImage, scalePermille: Int): LumaImage {
    require(scalePermille > 0) { "Scale must be positive" }
    val width = max(1, (source.width * scalePermille / 1_000f).roundToInt())
    val height = max(1, (source.height * scalePermille / 1_000f).roundToInt())
    if (width == source.width && height == source.height) return source
    val pixels = ByteArray(width * height)
    for (y in 0 until height) {
        val sourceY = if (height == 1) 0f else y * (source.height - 1f) / (height - 1f)
        val top = sourceY.toInt()
        val bottom = minOf(top + 1, source.height - 1)
        val verticalWeight = sourceY - top
        for (x in 0 until width) {
            val sourceX = if (width == 1) 0f else x * (source.width - 1f) / (width - 1f)
            val left = sourceX.toInt()
            val right = minOf(left + 1, source.width - 1)
            val horizontalWeight = sourceX - left
            val topValue = source[left, top] * (1f - horizontalWeight) + source[right, top] * horizontalWeight
            val bottomValue = source[left, bottom] * (1f - horizontalWeight) +
                source[right, bottom] * horizontalWeight
            pixels[y * width + x] = (
                topValue * (1f - verticalWeight) + bottomValue * verticalWeight
                ).roundToInt().toByte()
        }
    }
    return LumaImage(width, height, pixels)
}

private fun scalePermilles(tolerancePermille: Int): IntArray = when (tolerancePermille) {
    0 -> intArrayOf(1_000)
    50 -> intArrayOf(1_000, 950, 1_050)
    100 -> intArrayOf(1_000, 950, 1_050, 900, 1_100)
    else -> error("Unsupported scale tolerance")
}

private data class ScoredPosition(
    val left: Int,
    val top: Int,
    val scorePermille: Int,
    val width: Int,
    val height: Int,
) {
    val centerX: Int
        get() = left + width / 2
    val centerY: Int
        get() = top + height / 2
}

private const val MIN_TEMPLATE_VARIANCE = 4
private const val MAX_COARSE_SAMPLES = 64
private const val MAX_FINE_SAMPLES = 512
private const val MAX_COARSE_CANDIDATES = 12
private const val MAX_REFINED_CANDIDATES = 24
private val SUPPORTED_SCALE_TOLERANCES = setOf(0, 50, 100)
