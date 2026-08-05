package com.aiindexfinger.data

internal suspend fun clearRunHistory(clear: suspend () -> Unit) {
    clear()
}