package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.Path

object FileWalkUp {

    fun find(startDir: Path, fileName: String): Result<Path?> = runCatching {
        var dir = startDir.toRealPath()
        while (true) {
            val candidate = dir.resolve(fileName)
            if (Files.isRegularFile(candidate)) return@runCatching candidate
            dir = dir.parent ?: break
        }
        null
    }
}
