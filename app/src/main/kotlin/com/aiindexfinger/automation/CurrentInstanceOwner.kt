package com.aiindexfinger.automation

internal class CurrentInstanceOwner<T : Any> {
    private var current: T? = null

    @Synchronized
    fun claim(instance: T): T? {
        val previous = current
        current = instance
        return previous
    }

    @Synchronized
    fun release(instance: T): Boolean {
        if (current !== instance) return false
        current = null
        return true
    }

    @Synchronized
    fun isCurrent(instance: T): Boolean = current === instance

    @Synchronized
    fun get(): T? = current
}