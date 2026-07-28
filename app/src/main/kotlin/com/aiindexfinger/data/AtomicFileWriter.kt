package com.aiindexfinger.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object AtomicFileWriter {
    fun write(target: File, content: String) {
        val temporaryFile = File(target.parentFile, "${target.name}.tmp")
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
    }
}