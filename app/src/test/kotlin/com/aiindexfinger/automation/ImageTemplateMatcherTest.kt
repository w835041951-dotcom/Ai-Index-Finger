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
            TemplateMatchResult.Unique(13, 15, 1_000),
            matchTemplate(screen, template, 920, 25),
        )
    }

    @Test
    fun `exact match evaluates each fine position only once`() {
        val template = pattern(12, 12)
        val screen = canvas(40, 36, listOf(7 to 9), template)

        val measurement = matchTemplateMeasured(screen, template, 920, 25)

        assertEquals(TemplateMatchResult.Unique(13, 15, 1_000), measurement.result)
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
        assertEquals(186, measurement.fineEvaluations)
    }

    @Test
    fun `equal spatially distinct matches remain ambiguous with zero margin`() {
        val template = pattern(12, 12)
        val screen = canvas(48, 40, listOf(3 to 4, 29 to 20), template)

        assertEquals(TemplateMatchResult.Ambiguous, matchTemplate(screen, template, 920, 0))
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
            TemplateMatchResult.Unique(28, 24, 1_000),
            matchTemplate(screen, template, 1_000, 25, 100),
        )
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
}