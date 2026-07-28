package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals

class VariableTemplateTest {
    @Test
    fun `extracts and renders multiple variable placeholders`() {
        val template = "Order-${'$'}{orderId}-${'$'}{status}-${'$'}{orderId}"

        assertEquals(setOf("orderId", "status"), template.templateVariables())
        assertEquals(
            "Order-42-ready-42",
            template.renderTemplate { name -> mapOf("orderId" to "42", "status" to "ready").getValue(name) },
        )
    }
}