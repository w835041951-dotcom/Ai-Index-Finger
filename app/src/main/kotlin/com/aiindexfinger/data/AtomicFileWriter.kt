package com.aiindexfinger.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object AtomicFileWriter {
    fun write(target: File, content: String) = withTargetLock(target) {
        writeLocked(target, content)
    }

    fun cleanupTemporary(target: File): Boolean = runCatching {
        withTargetLock(target) {
            val temporaryFile = temporaryFile(target)
            !temporaryFile.exists() || Files.deleteIfExists(temporaryFile.toPath())
        }
    }.getOrDefault(false)

    private fun <T> withTargetLock(target: File, action: () -> T): T {
        val key = target.toPath().toAbsolutePath().normalize().toString()
        val lock = synchronized(targetLocks) {
            targetLocks.getOrPut(key, ::TargetLock).also { it.users++ }
        }
        return try {
            synchronized(lock.monitor) {
                action()
            }
        } finally {
            synchronized(targetLocks) {
                lock.users--
                if (lock.users == 0 && targetLocks[key] === lock) targetLocks.remove(key)
            }
        }
    }

    private fun writeLocked(target: File, content: String) {
        val temporaryFile = temporaryFile(target)
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.writer(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                    output.fd.sync()
                }
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Throwable) {
            runCatching { Files.deleteIfExists(temporaryFile.toPath()) }
            throw error
        }
    }

    private fun temporaryFile(target: File) = File(target.parentFile, "${target.name}.tmp")

    private class TargetLock(
        val monitor: Any = Any(),
        var users: Int = 0,
    )

    private val targetLocks = mutableMapOf<String, TargetLock>()
}