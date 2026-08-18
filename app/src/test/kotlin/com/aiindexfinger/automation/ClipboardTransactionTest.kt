package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardTransactionTest {
    @Test
    fun hiddenClipboardIsUnavailableOnAndroidTenAndNewer() {
        assertEquals(ClipboardSnapshot.Empty, missingClipboardSnapshot(androidSdk = 28))
        assertEquals(ClipboardSnapshot.Unavailable, missingClipboardSnapshot(androidSdk = 29))
        assertEquals(ClipboardSnapshot.Unavailable, missingClipboardSnapshot(androidSdk = 36))
    }

    @Test
    fun successfulPasteRestoresOriginalClipboard() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Content(FakeClip("original")))
        val transaction = ClipboardTransaction(adapter) { "operation-1" }

        assertTrue(transaction.paste("workflow text") { true })

        assertEquals(FakeClip("original"), adapter.currentClip)
    }

    @Test
    fun failedPasteStillRestoresOriginalClipboard() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Content(FakeClip("original")))
        val transaction = ClipboardTransaction(adapter) { "operation-1" }

        assertFalse(transaction.paste("workflow text") { false })

        assertEquals(FakeClip("original"), adapter.currentClip)
    }

    @Test
    fun exceptionStillRestoresOriginalClipboard() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Content(FakeClip("original")))
        val transaction = ClipboardTransaction(adapter) { "operation-1" }

        assertThrows(IllegalStateException::class.java) {
            transaction.paste("workflow text") { error("paste failed") }
        }

        assertEquals(FakeClip("original"), adapter.currentClip)
    }

    @Test
    fun externalClipboardChangeIsNeverOverwritten() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Content(FakeClip("original")))
        val transaction = ClipboardTransaction(adapter) { "operation-1" }

        transaction.paste("same text") {
            adapter.currentClip = FakeClip(text = "same text", token = "another-operation")
            true
        }

        assertEquals(FakeClip("same text", "another-operation"), adapter.currentClip)
    }

    @Test
    fun emptyClipboardIsClearedAfterPaste() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Empty)
        val transaction = ClipboardTransaction(adapter) { "operation-1" }

        transaction.paste("workflow text") { true }

        assertNull(adapter.currentClip)
        assertEquals(1, adapter.clearCount)
    }

    @Test
    fun unavailableClipboardIsNotOverwritten() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Unavailable)
        val transaction = ClipboardTransaction(adapter) { "operation-1" }
        var pasteAttempted = false

        assertFalse(transaction.paste("workflow text") {
            pasteAttempted = true
            true
        })

        assertFalse(pasteAttempted)
        assertEquals(0, adapter.setCount)
    }

    @Test
    fun unavailableClipboardHasDistinctPasteResult() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Unavailable)

        assertEquals(
            ClipboardPasteResult.ClipboardUnavailable,
            ClipboardTransaction(adapter).pasteResult("workflow text") { true },
        )
    }

    @Test
    fun consecutivePasteOperationsUseIndependentOwnershipTokens() {
        val adapter = FakeClipboardAdapter(ClipboardSnapshot.Content(FakeClip("original")))
        val tokens = ArrayDeque(listOf("operation-1", "operation-2"))
        val transaction = ClipboardTransaction(adapter) { tokens.removeFirst() }

        transaction.paste("first") { true }
        transaction.paste("second") { true }

        assertEquals(listOf("operation-1", "operation-2"), adapter.temporaryTokens)
        assertEquals(FakeClip("original"), adapter.currentClip)
    }
}

private data class FakeClip(val text: String, val token: String? = null)

private class FakeClipboardAdapter(
    private val snapshot: ClipboardSnapshot<FakeClip>,
) : ClipboardAdapter<FakeClip> {
    var currentClip: FakeClip? = (snapshot as? ClipboardSnapshot.Content)?.clip
    var clearCount = 0
    var setCount = 0
    val temporaryTokens = mutableListOf<String>()

    override fun capture(): ClipboardSnapshot<FakeClip> = when (snapshot) {
        is ClipboardSnapshot.Content -> ClipboardSnapshot.Content(snapshot.clip.copy())
        ClipboardSnapshot.Empty -> ClipboardSnapshot.Empty
        ClipboardSnapshot.Unavailable -> ClipboardSnapshot.Unavailable
    }

    override fun temporaryClip(text: String, token: String): FakeClip {
        temporaryTokens += token
        return FakeClip(text, token)
    }

    override fun setPrimaryClip(clip: FakeClip) {
        setCount += 1
        currentClip = clip
    }

    override fun primaryClipToken(): String? = currentClip?.token

    override fun clearPrimaryClip() {
        clearCount += 1
        currentClip = null
    }
}
