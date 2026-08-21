package com.aiindexfinger.automation

import com.aiindexfinger.model.ImageClickSelectionMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class LumaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xff
}

sealed interface TemplateMatchResult {
    data class Unique(
        val centerX: Int,
        val centerY: Int,
        val scorePermille: Int,
        val width: Int = 1,
        val height: Int = 1,
        val scalePermille: Int = 1_000,
    ) : TemplateMatchResult

    data object NoMatch : TemplateMatchResult
    data object Ambiguous : TemplateMatchResult
}

typealias TemplateMatchCandidate = TemplateMatchResult.Unique

val TemplateMatchCandidate.left: Int
    get() = centerX - width / 2

val TemplateMatchCandidate.top: Int
    get() = centerY - height / 2

internal data class TemplateMatchPlan(
    val candidates: List<TemplateMatchCandidate>,
    val rawCandidateCount: Int,
    val candidatesTruncated: Boolean,
) {
    init {
        require(candidates.size <= MAX_RAW_CANDIDATES) { "Too many template match candidates" }
    }
}

internal fun matchTemplate(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    ambiguityMarginPermille: Int,
    scaleTolerancePermille: Int = 0,
    searchRegions: List<ImageCropBounds>? = null,
    checkCancellation: () -> Unit = {},
): TemplateMatchResult = matchTemplatePlan(
    screen,
    template,
    minimumScorePermille,
    scaleTolerancePermille,
    searchRegions,
    checkCancellation,
).candidates.firstOrNull() ?: TemplateMatchResult.NoMatch

@Suppress("UNUSED_PARAMETER")
internal fun matchTemplatePlan(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    scaleTolerancePermille: Int = 0,
    searchRegions: List<ImageCropBounds>? = null,
    checkCancellation: () -> Unit = {},
): TemplateMatchPlan = matchTemplateInternal(
    screen = screen,
    template = template,
    minimumScorePermille = minimumScorePermille,
    scaleTolerancePermille = scaleTolerancePermille,
    searchRegions = searchRegions,
    checkCancellation = checkCancellation,
)

internal fun selectTemplateMatchCandidates(
    plan: TemplateMatchPlan,
    selectionMode: ImageClickSelectionMode,
    maxClicks: Int,
): List<TemplateMatchCandidate> {
    require(maxClicks in 1..MAX_RAW_CANDIDATES) { "Image click count is out of range" }
    return when (selectionMode) {
        ImageClickSelectionMode.BestMatch -> plan.candidates
            .sortedWith(compareByDescending<TemplateMatchCandidate> { it.scorePermille }
                .thenBy { it.centerY }
                .thenBy { it.centerX })
            .take(1)
        ImageClickSelectionMode.AllMatches -> plan.candidates
            .sortedWith(compareBy<TemplateMatchCandidate> { it.centerY }.thenBy { it.centerX })
            .take(maxClicks)
    }
}

internal fun matchTemplateMeasured(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    ambiguityMarginPermille: Int,
    scaleTolerancePermille: Int = 0,
    searchRegions: List<ImageCropBounds>? = null,
    checkCancellation: () -> Unit = {},
): TemplateMatchMeasurement {
    val work = TemplateMatchingWork()
    val plan = matchTemplateInternal(
        screen = screen,
        template = template,
        minimumScorePermille = minimumScorePermille,
        scaleTolerancePermille = scaleTolerancePermille,
        searchRegions = searchRegions,
        checkCancellation = checkCancellation,
        work = work,
    )
    return TemplateMatchMeasurement(plan, work.exactEvaluations, work.fineEvaluations)
}

@Suppress("UNUSED_PARAMETER")
private fun matchTemplateInternal(
    screen: LumaImage,
    template: LumaImage,
    minimumScorePermille: Int,
    scaleTolerancePermille: Int,
    searchRegions: List<ImageCropBounds>?,
    checkCancellation: () -> Unit,
    work: TemplateMatchingWork? = null,
): TemplateMatchPlan {
    require(scaleTolerancePermille in SUPPORTED_SCALE_TOLERANCES) { "Unsupported scale tolerance" }
    if (templateVariance(template) < MIN_TEMPLATE_VARIANCE) return TemplateMatchPlan(emptyList(), 0, false)

    val scaledTemplates = scalePermilles(scaleTolerancePermille).map { scalePermille ->
        checkCancellation()
        scalePermille to scaleLumaImage(template, scalePermille)
    }
    var rawCandidateCount = 0
    var candidatesTruncated = false
    val rawCandidates = scaledTemplates.flatMap { (scalePermille, scaledTemplate) ->
        checkCancellation()
        val exact = findExactCandidates(
            screen,
            scaledTemplate,
            searchRegions,
            checkCancellation,
            work,
        )
        val refined = if (exact.truncated) {
            emptyList()
        } else {
            findCandidates(
                screen,
                scaledTemplate,
                searchRegions,
                checkCancellation,
                work,
            )
        }
        val scaleCandidates = (exact.candidates + refined)
            .map { candidate -> candidate.copy(scalePermille = scalePermille) }
            .sortedWith(scoredPositionOrder)
        rawCandidateCount += scaleCandidates.size.coerceAtMost(MAX_RAW_CANDIDATES_PER_SCALE)
        candidatesTruncated = candidatesTruncated || exact.truncated ||
            scaleCandidates.size > MAX_RAW_CANDIDATES_PER_SCALE
        scaleCandidates.take(MAX_RAW_CANDIDATES_PER_SCALE)
    }
    val candidates = nonMaximumSuppression(rawCandidates)
        .filter { it.scorePermille >= minimumScorePermille }
    return TemplateMatchPlan(
        candidates = candidates,
        rawCandidateCount = rawCandidateCount,
        candidatesTruncated = candidatesTruncated,
    )
}

private data class CandidateSearch(
    val candidates: List<ScoredPosition>,
    val truncated: Boolean,
)

private fun findExactCandidates(
    screen: LumaImage,
    template: LumaImage,
    searchRegions: List<ImageCropBounds>?,
    checkCancellation: () -> Unit,
    work: TemplateMatchingWork?,
): CandidateSearch {
    if (template.width > screen.width || template.height > screen.height) return CandidateSearch(emptyList(), false)
    val regions = normalizedSearchRegions(screen, template, searchRegions) ?: return CandidateSearch(emptyList(), false)
    val anchors = exactMatchAnchors(template)
    val accepted = mutableListOf<ScoredPosition>()
    regions.forEach { region ->
        val endY = region.bottom - template.height
        val endX = region.right - template.width
        for (top in region.top..endY) {
            checkCancellation()
            for (left in region.left..endX) {
                if (work != null) work.exactEvaluations++
                if (anchors.any { index ->
                        val x = index % template.width
                        val y = index / template.width
                        screen[left + x, top + y] != template[x, y]
                    }
                ) continue
                if (!pixelsEqual(screen, template, left, top, checkCancellation)) continue
                val candidate = ScoredPosition(
                    left,
                    top,
                    1_000,
                    template.width,
                    template.height,
                )
                if (accepted.all { existing ->
                        intersectionOverUnion(existing, candidate) < NMS_IOU_THRESHOLD
                    }
                ) {
                    if (accepted.size == MAX_RAW_CANDIDATES_PER_SCALE) {
                        return CandidateSearch(accepted, truncated = true)
                    }
                    accepted += candidate
                }
            }
        }
    }
    return CandidateSearch(accepted, truncated = false)
}

private fun exactMatchAnchors(template: LumaImage): IntArray {
    val indices = template.pixels.indices
    val darkest = indices.minBy { template.pixels[it].toInt() and 0xff }
    val lightest = indices.maxBy { template.pixels[it].toInt() and 0xff }
    return intArrayOf(0, template.pixels.lastIndex, template.pixels.size / 2, darkest, lightest)
        .distinct()
        .toIntArray()
}

private fun pixelsEqual(
    screen: LumaImage,
    template: LumaImage,
    left: Int,
    top: Int,
    checkCancellation: () -> Unit,
): Boolean {
    for (y in 0 until template.height) {
        checkCancellation()
        for (x in 0 until template.width) {
            if (screen[left + x, top + y] != template[x, y]) return false
        }
    }
    return true
}

private fun nonMaximumSuppression(candidates: List<ScoredPosition>): List<TemplateMatchCandidate> = candidates
    .sortedWith(scoredPositionOrder)
    .fold(mutableListOf<ScoredPosition>()) { accepted, candidate ->
        if (accepted.all { existing -> intersectionOverUnion(existing, candidate) < NMS_IOU_THRESHOLD }) {
            accepted += candidate
        }
        accepted
    }
    .map(ScoredPosition::toTemplateMatchCandidate)

private fun intersectionOverUnion(first: ScoredPosition, second: ScoredPosition): Float {
    val intersectionWidth = (minOf(first.left + first.width, second.left + second.width) -
        maxOf(first.left, second.left)).coerceAtLeast(0)
    val intersectionHeight = (minOf(first.top + first.height, second.top + second.height) -
        maxOf(first.top, second.top)).coerceAtLeast(0)
    val intersection = intersectionWidth.toLong() * intersectionHeight
    val union = first.width.toLong() * first.height + second.width.toLong() * second.height - intersection
    return if (union == 0L) 0f else intersection.toFloat() / union
}

private fun findCandidates(
    screen: LumaImage,
    template: LumaImage,
    searchRegions: List<ImageCropBounds>?,
    checkCancellation: () -> Unit,
    work: TemplateMatchingWork?,
): List<ScoredPosition> {
    if (template.width > screen.width || template.height > screen.height) return emptyList()
    val coarseStride = max(2, minOf(template.width, template.height) / 6)
    val coarseCandidates = mutableListOf<ScoredPosition>()
    val normalizedRegions = normalizedSearchRegions(screen, template, searchRegions) ?: return emptyList()
     val positionWidth = screen.width - template.width + 1
     val positionHeight = screen.height - template.height + 1
     var visitedCoordinates = BooleanArray(positionWidth * positionHeight)
    val coarseRegions = normalizedRegions
    coarseRegions.forEach { region ->
        val endY = region.bottom - template.height
        val endX = region.right - template.width
        for (top in axisPositions(region.top, endY, coarseStride)) {
            checkCancellation()
            for (left in axisPositions(region.left, endX, coarseStride)) {
                val coordinate = top * positionWidth + left
                if (visitedCoordinates[coordinate]) continue
                visitedCoordinates[coordinate] = true
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
    }

    val refined = mutableListOf<ScoredPosition>()
    visitedCoordinates = BooleanArray(positionWidth * positionHeight)
    coarseCandidates.forEach { candidate ->
        val startX = (candidate.left - coarseStride).coerceAtLeast(0)
        val endX = (candidate.left + coarseStride).coerceAtMost(screen.width - template.width)
        val startY = (candidate.top - coarseStride).coerceAtLeast(0)
        val endY = (candidate.top + coarseStride).coerceAtMost(screen.height - template.height)
        for (top in startY..endY) {
            checkCancellation()
            for (left in startX..endX) {
                if (normalizedRegions.none { region ->
                        left >= region.left && top >= region.top &&
                            left + template.width <= region.right && top + template.height <= region.bottom
                    }
                ) continue
                val coordinate = top * positionWidth + left
                if (visitedCoordinates[coordinate]) continue
                visitedCoordinates[coordinate] = true
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

private fun normalizedSearchRegions(
    screen: LumaImage,
    template: LumaImage,
    searchRegions: List<ImageCropBounds>?,
): List<ImageCropBounds>? {
    val regions = searchRegions?.mapNotNull { region ->
        ImageCropBounds(
            left = region.left.coerceIn(0, screen.width),
            top = region.top.coerceIn(0, screen.height),
            right = region.right.coerceIn(0, screen.width),
            bottom = region.bottom.coerceIn(0, screen.height),
        ).takeIf {
            it.right - it.left >= template.width && it.bottom - it.top >= template.height
        }
    }?.distinct() ?: listOf(ImageCropBounds(0, 0, screen.width, screen.height))
    return regions.takeIf(List<ImageCropBounds>::isNotEmpty)
}

private fun axisPositions(start: Int, end: Int, stride: Int): Sequence<Int> = sequence {
    if (start > end) return@sequence
    var position = start
    while (position <= end) {
        yield(position)
        position += stride
    }
    if ((end - start) % stride != 0) yield(end)
}

internal data class TemplateMatchMeasurement(
    val plan: TemplateMatchPlan,
    val exactEvaluations: Int,
    val fineEvaluations: Int,
) {
    val result: TemplateMatchResult get() = plan.candidates.firstOrNull() ?: TemplateMatchResult.NoMatch
}

private class TemplateMatchingWork(
    var exactEvaluations: Int = 0,
    var fineEvaluations: Int = 0,
)

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
    if (totalDifference == 0L) return if (sampleStride == 1) 1_000 else 999
    val denominator = count * 255L
    val penalty = (totalDifference * 1_000L + denominator - 1) / denominator
    return (1_000L - penalty).toInt().coerceIn(0, 999)
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
    val scalePermille: Int = 1_000,
) {
    val centerX: Int
        get() = left + width / 2
    val centerY: Int
        get() = top + height / 2

    fun toTemplateMatchCandidate(): TemplateMatchCandidate = TemplateMatchCandidate(
        centerX = centerX,
        centerY = centerY,
        scorePermille = scorePermille,
        width = width,
        height = height,
        scalePermille = scalePermille,
    )
}

private const val MIN_TEMPLATE_VARIANCE = 4
private const val MAX_COARSE_SAMPLES = 64
private const val MAX_FINE_SAMPLES = 512
private const val MAX_RAW_CANDIDATES_PER_SCALE = 100
private const val MAX_RAW_CANDIDATES = MAX_RAW_CANDIDATES_PER_SCALE * 5
private const val MAX_COARSE_CANDIDATES = 12
private const val MAX_REFINED_CANDIDATES = MAX_RAW_CANDIDATES_PER_SCALE
private const val NMS_IOU_THRESHOLD = 0.5f
private val SUPPORTED_SCALE_TOLERANCES = setOf(0, 50, 100)
private val scoredPositionOrder = compareByDescending<ScoredPosition> { it.scorePermille }
    .thenBy { it.centerY }
    .thenBy { it.centerX }
