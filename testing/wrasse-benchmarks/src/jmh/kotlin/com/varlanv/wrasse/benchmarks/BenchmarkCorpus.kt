package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.lang.FileWalkUp
import java.io.File
import java.nio.file.Path

object BenchmarkCorpus {

    fun load(): String {
        val repoRoot = FileWalkUp.find(Path.of("").toAbsolutePath()) { it == "settings.gradle.kts" }
            .getOrThrow()
            ?.parent
            ?: error("settings.gradle.kts not found walking up from ${Path.of("").toAbsolutePath()}")

        val sep = File.separator
        val sources = repoRoot.toFile()
            .walkTopDown()
            .filter {
                it.isFile &&
                    it.extension == "kt" &&
                    "${sep}build$sep" !in it.path &&
                    "${sep}.gradle$sep" !in it.path &&
                    "${sep}wrasse-benchmarks$sep" !in it.path
            }
            .sortedByDescending { it.length() }
            .toList()
        check(sources.isNotEmpty()) { "no .kt sources found under $repoRoot" }

        return buildString {
            appendLine("package com.varlanv.wrasse.benchmarks.generated")
            appendLine()
            for (file in sources) {
                for (line in file.readLines()) {
                    if (line.startsWith("package ") || line.startsWith("@file:")) continue
                    appendLine(line)
                }
                appendLine()
            }
        }
    }
}
