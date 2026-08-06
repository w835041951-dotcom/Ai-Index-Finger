package com.aiindexfinger.automation

import com.aiindexfinger.model.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveActionStepFactoryTest {
    @Test
    fun coordinateCandidateCreatesTapStep() {
        assertEquals(
            Step.Tap(id = "step-a", x = 120, y = 340),
            LiveActionCandidate.Coordinate(120, 340).toStep("step-a"),
        )
    }

    @Test
    fun imageCandidateCreatesImageClickStepWithRuntimeDefaults() {
        val step = LiveActionCandidate.Image(
            packageName = "com.example.target",
            templatePngBase64 = "template",
            templateWidth = 24,
            templateHeight = 18,
        ).toStep("step-b") as Step.ImageClick

        assertEquals("step-b", step.id)
        assertEquals("com.example.target", step.packageName)
        assertEquals("template", step.templatePngBase64)
        assertEquals(24, step.templateWidth)
        assertEquals(18, step.templateHeight)
        assertEquals(920, step.minimumScorePermille)
        assertEquals(25, step.ambiguityMarginPermille)
    }
}