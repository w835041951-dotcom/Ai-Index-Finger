package com.aiindexfinger.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import java.util.UUID

internal sealed interface ClipboardSnapshot<out T> {
    data object Empty : ClipboardSnapshot<Nothing>
    data object Unavailable : ClipboardSnapshot<Nothing>
    data class Content<T>(val clip: T) : ClipboardSnapshot<T>
}

internal interface ClipboardAdapter<T> {
    fun capture(): ClipboardSnapshot<T>
    fun temporaryClip(text: String, token: String): T
    fun setPrimaryClip(clip: T)
    fun primaryClipToken(): String?
    fun clearPrimaryClip()
}

internal class ClipboardTransaction<T>(
    private val adapter: ClipboardAdapter<T>,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun paste(text: String, action: () -> Boolean): Boolean {
        val original = adapter.capture()
        if (original == ClipboardSnapshot.Unavailable) return false

        val token = tokenFactory()
        adapter.setPrimaryClip(adapter.temporaryClip(text, token))
        return try {
            action()
        } finally {
            if (adapter.primaryClipToken() == token) {
                when (original) {
                    is ClipboardSnapshot.Content -> adapter.setPrimaryClip(original.clip)
                    ClipboardSnapshot.Empty -> adapter.clearPrimaryClip()
                    ClipboardSnapshot.Unavailable -> Unit
                }
            }
        }
    }
}

internal class AndroidClipboardAdapter(
    private val clipboardManager: ClipboardManager,
    private val clipLabel: String,
) : ClipboardAdapter<ClipData> {
    override fun capture(): ClipboardSnapshot<ClipData> {
        if (!clipboardManager.hasPrimaryClip()) return ClipboardSnapshot.Empty
        val currentClip = clipboardManager.primaryClip ?: return ClipboardSnapshot.Unavailable
        return ClipboardSnapshot.Content(ClipData(currentClip))
    }

    override fun temporaryClip(text: String, token: String): ClipData =
        ClipData.newPlainText(clipLabel, text).apply {
            description.extras = PersistableBundle().apply {
                putString(CLIP_TOKEN_KEY, token)
            }
        }

    override fun setPrimaryClip(clip: ClipData) {
        clipboardManager.setPrimaryClip(clip)
    }

    override fun primaryClipToken(): String? =
        clipboardManager.primaryClipDescription?.extras?.getString(CLIP_TOKEN_KEY)

    override fun clearPrimaryClip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(clipLabel, ""))
        }
    }

    private companion object {
        const val CLIP_TOKEN_KEY = "com.aiindexfinger.clipboard.OPERATION_TOKEN"
    }
}
