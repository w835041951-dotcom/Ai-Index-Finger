package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class ImageTemplateMatcherTest {
    @Test
    fun `finds one exact template and returns its center`() {
        val template = pattern(12, 12)
        val screen = canvas(40, 36, listOf(7 to 9), template)

        assertEquals(
            TemplateMatchResult.Unique(13, 15, 1_000, 12, 12),
            matchTemplate(screen, template, 920, 25),
        )
    }

    @Test
    fun `native one to one matching finds every integer placement including edges`() {
        val template = pattern(12, 12)

        for (top in 0..24) {
            for (left in 0..28) {
                assertEquals(
                    "placement $left,$top",
                    TemplateMatchResult.Unique(left + 6, top + 6, 1_000, 12, 12),
                    matchTemplate(
                        canvas(40, 36, listOf(left to top), template),
                        template,
                        1_000,
                        25,
                    ),
                )
            }
        }
    }

    @Test
    fun `native exact target is not pruned by aligned near match decoys`() {
        val template = pattern(12, 12)
        val width = 512
        val height = 512
        val pixels = ByteArray(width * height) { 3 }
        val decoy = template.pixels.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val decoyPositions = List(20) { index ->
            20 + (index % 5) * 80 to 20 + (index / 5) * 80
        }
        decoyPositions.forEach { (left, top) ->
            copyPixels(decoy, template.width, template.height, pixels, width, left, top)
        }
        val exactLeft = 451
        val exactTop = 451
        copyPixels(
            template.pixels,
            template.width,
            template.height,
            pixels,
            width,
            exactLeft,
            exactTop,
        )

        assertEquals(
            TemplateMatchResult.Unique(exactLeft + 6, exactTop + 6, 1_000, 12, 12),
            matchTemplate(
                LumaImage(width, height, pixels),
                template,
                minimumScorePermille = 1_000,
                ambiguityMarginPermille = 25,
            ),
        )
    }

    @Test
    fun `exact match participates in approximate ambiguity ranking`() {
        val template = pattern(12, 12)
        val screen = canvas(40, 36, listOf(7 to 9), template)

        val measurement = matchTemplateMeasured(screen, template, 920, 25)

        assertEquals(TemplateMatchResult.Unique(13, 15, 1_000, 12, 12), measurement.result)
        assertEquals(189, measurement.fineEvaluations)
    }

    @Test
    fun `near match evaluates each fine position only once`() {
        val template = pattern(12, 12)
        val nearMatch = LumaImage(
            template.width,
            template.height,
            template.pixels.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() },
        )
        val screen = canvas(40, 36, listOf(7 to 9), nearMatch)

        val measurement = matchTemplateMeasured(screen, template, 920, 25)

        assertTrue(measurement.result is TemplateMatchResult.Unique)
        assertEquals(189, measurement.fineEvaluations)
    }

    @Test
    fun `rejects absent and low variance templates`() {
        val template = pattern(12, 12)
        val blank = LumaImage(32, 32, ByteArray(32 * 32))
        val uniformTemplate = LumaImage(12, 12, ByteArray(12 * 12) { 80 })

        assertEquals(TemplateMatchResult.NoMatch, matchTemplate(blank, template, 920, 25))
        assertEquals(TemplateMatchResult.NoMatch, matchTemplate(blank, uniformTemplate, 920, 25))
    }

    @Test
    fun `rejects two equally strong spatially distinct matches`() {
        val template = pattern(12, 12)
        val screen = canvas(48, 40, listOf(3 to 4, 29 to 20), template)

        val measurement = matchTemplateMeasured(screen, template, 920, 25)

        assertEquals(TemplateMatchResult.Ambiguous, measurement.result)
        assertEquals(0, measurement.fineEvaluations)
    }

    @Test
    fun `equal spatially distinct matches remain ambiguous with zero margin`() {
        val template = pattern(12, 12)
        val screen = canvas(48, 40, listOf(3 to 4, 29 to 20), template)

        assertEquals(TemplateMatchResult.Ambiguous, matchTemplate(screen, template, 920, 0))
    }

    @Test
    fun `exact target still requires configured lead over near match`() {
        val template = pattern(12, 12)
        val nearMatch = template.pixels.map { value ->
            ((value.toInt() and 0xff) + 2).coerceAtMost(255).toByte()
        }.toByteArray()
        val pixels = ByteArray(48 * 40) { 3 }
        copyPixels(template.pixels, 12, 12, pixels, 48, 3, 4)
        copyPixels(nearMatch, 12, 12, pixels, 48, 29, 20)
        val screen = LumaImage(48, 40, pixels)

        assertEquals(
            TemplateMatchResult.Ambiguous,
            matchTemplate(screen, template, 920, ambiguityMarginPermille = 25),
        )
        assertEquals(
            TemplateMatchResult.Unique(9, 10, 1_000, 12, 12),
            matchTemplate(screen, template, 920, ambiguityMarginPermille = 5),
        )
    }

    @Test
    fun `ignores identical matches outside target search region`() {
        val template = pattern(12, 12)
        val screen = canvas(48, 40, listOf(3 to 4, 29 to 19), template)

        assertEquals(TemplateMatchResult.Ambiguous, matchTemplate(screen, template, 920, 25))
        assertEquals(
            TemplateMatchResult.Unique(35, 25, 1_000, 12, 12),
            matchTemplate(
                screen,
                template,
                920,
                25,
                searchRegions = listOf(ImageCropBounds(29, 19, 41, 31)),
            ),
        )
    }

    @Test
    fun `score remains bounded for noisy screen`() {
        val template = pattern(12, 12)
        val noisy = LumaImage(24, 24, ByteArray(24 * 24) { ((it * 37) % 255).toByte() })

        val result = matchTemplate(noisy, template, 990, 10)

        assertTrue(result is TemplateMatchResult.NoMatch || result is TemplateMatchResult.Ambiguous)
    }

    @Test
    fun `scale tolerance finds a target enlarged by ten percent`() {
        val template = pattern(20, 20)
        val enlarged = scaleLumaImage(template, 1_100)
        val screen = canvas(64, 56, listOf(17 to 13), enlarged)

        assertEquals(
            TemplateMatchResult.NoMatch,
            matchTemplate(screen, template, 1_000, 25),
        )
        assertEquals(
            TemplateMatchResult.Unique(28, 24, 1_000, 22, 22),
            matchTemplate(screen, template, 1_000, 25, 100),
        )
    }

    @Test
    fun `every supported tolerance scale matches at all bitmap corners`() {
        val template = pattern(20, 20)
        listOf(900, 950, 1_050, 1_100).forEach { scale ->
            val scaled = scaleLumaImage(template, scale)
            val positions = listOf(
                0 to 0,
                (80 - scaled.width) to 0,
                0 to (64 - scaled.height),
                (80 - scaled.width) to (64 - scaled.height),
            )
            positions.forEach { (left, top) ->
                assertEquals(
                    "scale $scale at $left,$top",
                    TemplateMatchResult.Unique(
                        left + scaled.width / 2,
                        top + scaled.height / 2,
                        1_000,
                        scaled.width,
                        scaled.height,
                    ),
                    matchTemplate(
                        canvas(80, 64, listOf(left to top), scaled),
                        template,
                        1_000,
                        25,
                        100,
                    ),
                )
            }
        }
    }

    @Test
    fun `target region and scale tolerance compose without searching outside app`() {
        val template = pattern(20, 20)
        val enlarged = scaleLumaImage(template, 1_100)
        val pixels = canvas(80, 60, listOf(3 to 3), template).pixels.copyOf()
        for (y in 0 until enlarged.height) {
            for (x in 0 until enlarged.width) {
                pixels[(17 + y) * 80 + 45 + x] = enlarged[x, y].toByte()
            }
        }
        val screen = LumaImage(80, 60, pixels)
        val targetRegion = listOf(ImageCropBounds(45, 17, 67, 39))

        assertEquals(
            TemplateMatchResult.NoMatch,
            matchTemplate(screen, template, 1_000, 25, searchRegions = targetRegion),
        )
        assertEquals(
            TemplateMatchResult.Unique(56, 28, 1_000, 22, 22),
            matchTemplate(screen, template, 1_000, 25, 100, targetRegion),
        )
    }

    @Test
    fun `target region bounds exact pass work`() {
        val template = pattern(12, 12)
        val screen = LumaImage(100, 100, ByteArray(100 * 100) { 3 })

        val full = matchTemplateMeasured(screen, template, 920, 25)
        val targetOnly = matchTemplateMeasured(
            screen,
            template,
            920,
            25,
            searchRegions = listOf(ImageCropBounds(50, 50, 70, 70)),
        )

        assertEquals(7_921, full.exactEvaluations)
        assertEquals(81, targetOnly.exactEvaluations)
        assertTrue(targetOnly.fineEvaluations < full.fineEvaluations)
    }

    @Test
    fun `scale variants at one location are treated as one match`() {
        val template = pattern(20, 20)
        val screen = canvas(64, 56, listOf(17 to 13), scaleLumaImage(template, 1_100))

        assertTrue(matchTemplate(screen, template, 900, 100, 100) is TemplateMatchResult.Unique)
    }

    @Test
    fun `targets at different scales and positions remain ambiguous`() {
        val template = pattern(20, 20)
        val enlarged = scaleLumaImage(template, 1_100)
        val pixels = canvas(80, 64, listOf(3 to 3), template).pixels.copyOf()
        for (y in 0 until enlarged.height) {
            for (x in 0 until enlarged.width) {
                pixels[(30 + y) * 80 + 48 + x] = enlarged[x, y].toByte()
            }
        }
        val screen = LumaImage(80, 64, pixels)

        assertEquals(TemplateMatchResult.Ambiguous, matchTemplate(screen, template, 950, 25, 100))
    }

    @Test(expected = CancellationException::class)
    fun `stops matching when cancellation is requested`() {
        val template = pattern(24, 24)
        val screen = LumaImage(1_080, 2_400, ByteArray(1_080 * 2_400) { (it % 251).toByte() })
        var checks = 0

        matchTemplate(screen, template, 920, 25) {
            if (++checks == 5) throw CancellationException("cancelled")
        }
    }

    private fun pattern(width: Int, height: Int) = LumaImage(
        width,
        height,
        ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x + y) % 3 == 0) 230.toByte() else (20 + x * 3 + y).toByte()
        },
    )

    private fun canvas(
        width: Int,
        height: Int,
        positions: List<Pair<Int, Int>>,
        template: LumaImage,
    ): LumaImage {
        val pixels = ByteArray(width * height) { 3 }
        positions.forEach { (left, top) ->
            for (y in 0 until template.height) {
                for (x in 0 until template.width) {
                    pixels[(top + y) * width + left + x] = template[x, y].toByte()
                }
            }
        }
        return LumaImage(width, height, pixels)
    }

    private fun copyPixels(
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        destination: ByteArray,
        destinationWidth: Int,
        left: Int,
        top: Int,
    ) {
        for (y in 0 until sourceHeight) {
            source.copyInto(
                destination,
                destinationOffset = (top + y) * destinationWidth + left,
                startIndex = y * sourceWidth,
                endIndex = (y + 1) * sourceWidth,
            )
        }
    }
}