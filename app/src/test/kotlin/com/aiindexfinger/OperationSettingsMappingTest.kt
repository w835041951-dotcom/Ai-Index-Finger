package com.aiindexfinger

import com.aiindexfinger.model.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationSettingsMappingTest {
    @Test
    fun `image match percentages round trip every supported permille value`() {
        for (permille in 0..1_000) {
            assertEquals(permille, imageMatchPercentToPermille(imageMatchPercentText(permille)))
        }
    }

    @Test
    fun `image match percentage accepts bounded values with one decimal place`() {
        assertEquals(0, imageMatchPercentToPermille(" 0 "))
        assertEquals(25, imageMatchPercentToPermille("2.5"))
        assertEquals(920, imageMatchPercentToPermille("92.0"))
        assertEquals(1_000, imageMatchPercentToPermille("100.0"))
    }

    @Test
    fun `image match percentage rejects invalid precision and range`() {
        listOf("", "-1", ".5", "92.25", "100.1", "101", "value").forEach { value ->
            assertNull(value, imageMatchPercentToPermille(value))
        }
    }

    @Test
    fun `optional launch action trims values and clears blanks`() {
        assertEquals(
            "android.settings.WIFI_SETTINGS",
            normalizedOptionalText("  android.settings.WIFI_SETTINGS  "),
        )
        assertNull(normalizedOptionalText("   "))
    }

    @Test
    fun `unchanged imported text is preserved and edited text is trimmed`() {
        assertEquals(" source ", preserveUnchangedOrTrim(" source ", " source "))
        assertEquals("updated", preserveUnchangedOrTrim(" updated ", " source "))
    }

    @Test
    fun `set variable values preserve their source type when reopened and saved`() {
        val values = listOf(
            Value.Literal("ready"),
            Value.Variable("source"),
            Value.Template("Order-${'$'}{orderId}"),
        )

        values.forEach { original ->
            assertEquals(
                original,
                variableValueOrNull(variableValueMode(original), variableValueText(original)),
            )
        }
    }

    @Test
    fun `variable reference trims names and rejects blanks`() {
        assertEquals(Value.Variable("source"), variableValueOrNull(VariableValueMode.Variable, " source "))
        assertNull(variableValueOrNull(VariableValueMode.Variable, "   "))
    }

    @Test
    fun `comparison operands preserve all value source combinations`() {
        val values = listOf(
            Value.Literal("ready"),
            Value.Variable("source"),
            Value.Template("Order-${'$'}{orderId}"),
        )

        values.forEach { left ->
            values.forEach { right ->
                assertEquals(
                    left,
                    variableValueOrNull(variableValueMode(left), variableValueText(left)),
                )
                assertEquals(
                    right,
                    variableValueOrNull(variableValueMode(right), variableValueText(right)),
                )
            }
        }
    }
}