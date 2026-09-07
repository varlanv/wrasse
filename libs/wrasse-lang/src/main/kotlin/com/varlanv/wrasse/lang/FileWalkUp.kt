package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.Path

object FileWalkUp {
    fun find(startDir: Path, predicate: (String) -> Boolean): Result<Path?> = runCatching {
        var dir = startDir.toRealPath()
        while (true) {
            Files
                .list(dir)
                .use { stream ->
                    val match = stream
                        .filter { Files.isRegularFile(it) && predicate(it.fileName.toString()) }
                        .findFirst()
                    if (match.isPresent) return@runCatching match.get()
                }
            dir = dir.parent ?: break
        }
        null
    }

    fun findNamed(startDir: Path, fileName: String): Result<Path?> = runCatching {
        var dir = startDir.toRealPath()
        while (true) {
            val candidate = dir.resolve(fileName)
            if (Files.isRegularFile(candidate)) return@runCatching candidate
            dir = dir.parent ?: break
        }
        null
    }
}
