package com.aiindexfinger.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReadNodeTextPostProcessTest {
    @Test
    fun `applies trim regex split and case conversion in order`() {
        val process = ReadNodeTextPostProcess(
            trim = true,
            regex = "ID:(\\w+-\\w+)",
            regexGroup = 1,
            splitDelimiter = "-",
            splitIndex = 1,
            caseTransform = ReadNodeTextCaseTransform.Uppercase,
        )

        assertEquals("B2", process.applyTo("  ID:a1-b2  "))
    }

    @Test
    fun `returns null when regex or split output is unavailable`() {
        assertNull(ReadNodeTextPostProcess(regex = "ID:(\\w+)").applyTo("missing"))
        assertNull(ReadNodeTextPostProcess(splitDelimiter = ",", splitIndex = 2).applyTo("one,two"))
        assertNull(ReadNodeTextPostProcess(regex = "(one)", regexGroup = 2).applyTo("one"))
    }

    @Test
    fun `rejects invalid processing settings`() {
        assertFailsWith<IllegalArgumentException> { ReadNodeTextPostProcess(regex = "[") }
        assertFailsWith<IllegalArgumentException> { ReadNodeTextPostProcess(regexGroup = -1) }
        assertFailsWith<IllegalArgumentException> { ReadNodeTextPostProcess(splitDelimiter = "") }
        assertFailsWith<IllegalArgumentException> { ReadNodeTextPostProcess(splitIndex = -1) }
    }
}