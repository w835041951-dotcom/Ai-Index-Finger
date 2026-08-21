package com.aiindexfinger.automation

import com.aiindexfinger.model.ImageClickSelectionMode
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
    fun `plans every spatially distinct exact match in screen order`() {
        val template = pattern(12, 12)
        val screen = canvas(48, 40, listOf(3 to 4, 29 to 20), template)

        val plan = matchTemplatePlan(screen, template, 920)

        assertEquals(
            listOf(
                TemplateMatchResult.Unique(9, 10, 1_000, 12, 12),
                TemplateMatchResult.Unique(35, 26, 1_000, 12, 12),
            ),
            plan.candidates,
        )
        assertEquals(
            listOf(TemplateMatchResult.Unique(9, 10, 1_000, 12, 12)),
            selectTemplateMatchCandidates(plan, ImageClickSelectionMode.BestMatch, 20),
        )
        assertEquals(
            plan.candidates,
            selectTemplateMatchCandidates(plan, ImageClickSelectionMode.AllMatches, 20),
        )
    }

    @Test
    fun `keeps nearby exact matches when their overlap is below NMS threshold`() {
        val template = periodicPattern(12, 12)
        val screen = LumaImage(
            width = 17,
            height = 12,
            pixels = ByteArray(17 * 12) { index ->
                val x = index % 17
                val y = index / 17
                (20 + (x % 5) * 30 + y * 3).toByte()
            },
        )

        assertEquals(
            listOf(TemplateMatchResult.Unique(6, 6, 1_000, 12, 12), TemplateMatchResult.Unique(11, 6, 1_000, 12, 12)),
            matchTemplatePlan(screen, template, 1_000).candidates,
        )
    }

    @Test
    fun `best match breaks exact-score ties from top to bottom then left to right`() {
        val template = pattern(12, 12)
        val screen = canvas(64, 40, listOf(29 to 4, 3 to 4, 40 to 20), template)

        val selected = selectTemplateMatchCandidates(
            matchTemplatePlan(screen, template, 920),
            ImageClickSelectionMode.BestMatch,
            20,
        )

        assertEquals(listOf(TemplateMatchResult.Unique(9, 10, 1_000, 12, 12)), selected)
    }

    @Test
    fun `best match ignores the legacy ambiguity margin`() {
        val template = pattern(12, 12)
        val nearMatch = template.pixels.map { value ->
            ((value.toInt() and 0xff) + 2).coerceAtMost(255).toByte()
        }.toByteArray()
        val pixels = ByteArray(48 * 40) { 3 }
        copyPixels(template.pixels, 12, 12, pixels, 48, 3, 4)
        copyPixels(nearMatch, 12, 12, pixels, 48, 29, 20)
        val screen = LumaImage(48, 40, pixels)

        assertEquals(
            TemplateMatchResult.Unique(9, 10, 1_000, 12, 12),
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

        assertEquals(
            listOf(9 to 10, 35 to 25),
            matchTemplatePlan(screen, template, 920).candidates.map { it.centerX to it.centerY },
        )
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
            TemplateMatchResult.Unique(28, 24, 1_000, 22, 22, 1_100),
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
                        scale,
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
            TemplateMatchResult.Unique(56, 28, 1_000, 22, 22, 1_100),
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
    fun `targets at different scales and positions remain separate candidates`() {
        val template = pattern(20, 20)
        val enlarged = scaleLumaImage(template, 1_100)
        val pixels = canvas(80, 64, listOf(3 to 3), template).pixels.copyOf()
        for (y in 0 until enlarged.height) {
            for (x in 0 until enlarged.width) {
                pixels[(30 + y) * 80 + 48 + x] = enlarged[x, y].toByte()
            }
        }
        val screen = LumaImage(80, 64, pixels)

        val plan = matchTemplatePlan(screen, template, 950, 100)

        assertEquals(2, plan.candidates.size)
        assertEquals(TemplateMatchResult.Unique(13, 13, 1_000, 20, 20, 1_000), plan.candidates.first())
    }

    @Test
    fun `caps raw exact candidates and records truncation`() {
        val template = nonRepeatingPattern(12, 12)
        val positions = buildList {
            repeat(11) { row ->
                repeat(10) { column -> add(column * 14 to row * 14) }
            }
        }

        val plan = matchTemplatePlan(canvas(152, 166, positions, template), template, 1_000)

        assertEquals(100, plan.rawCandidateCount)
        assertEquals(true, plan.candidatesTruncated)
        assertTrue(plan.candidates.size <= 100)
        assertEquals(
            plan.candidates.size,
            selectTemplateMatchCandidates(plan, ImageClickSelectionMode.AllMatches, 100).size,
        )
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

    private fun nonRepeatingPattern(width: Int, height: Int) = LumaImage(
        width,
        height,
        ByteArray(width * height) { index ->
            ((index * 73 + index / width * 41 + 19) % 251).toByte()
        },
    )

    private fun periodicPattern(width: Int, height: Int) = LumaImage(
        width,
        height,
        ByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (20 + (x % 5) * 30 + y * 3).toByte()
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