package com.aiindexfinger.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentInstanceOwnerTest {
    @Test
    fun staleInstanceCannotReleaseNewOwner() {
        val owner = CurrentInstanceOwner<Any>()
        val first = Any()
        val second = Any()

        assertNull(owner.claim(first))
        assertSame(first, owner.claim(second))

        assertFalse(owner.release(first))
        assertSame(second, owner.get())
        assertFalse(owner.isCurrent(first))
        assertTrue(owner.isCurrent(second))
    }

    @Test
    fun currentInstanceCanReleaseOwnership() {
        val owner = CurrentInstanceOwner<Any>()
        val instance = Any()
        assertNull(owner.claim(instance))

        assertTrue(owner.release(instance))

        assertNull(owner.get())
        assertFalse(owner.isCurrent(instance))
    }
}