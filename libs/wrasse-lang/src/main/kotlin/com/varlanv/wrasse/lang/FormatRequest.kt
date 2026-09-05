package com.varlanv.wrasse.lang

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The one-shot request file a build tool drops into a compilation's `fixOutputDir` right before a
 * run: one `key=value` per line, `timestamp=<epoch millis>` plus any of `formatting=true` (the
 * diagnostics that carry edits stay quiet) and `debugPerformance=true` (the run records timings,
 * see [PerfRecorder]). The compiler plugin consumes it on start — reads, deletes, and honors it
 * only when the timestamp parses and is at most [MAX_AGE_MILLIS] old. Never throws; anything
 * unreadable or malformed means [RunRequest.NONE].
 */
object FormatRequest {
    const val FILE_NAME = "format-request"
    const val MAX_AGE_MILLIS = 5L * 60 * 1_000

    fun write(
        dir: Path,
        now: Long = System.currentTimeMillis(),
        formatting: Boolean = true,
        debugPerformance: Boolean = false,
    ) {
        Files.createDirectories(dir)
        val text = StringBuilder("timestamp=$now\n")
        if (formatting) text.append("formatting=true\n")
        if (debugPerformance) text.append("debugPerformance=true\n")
        Files.write(dir.resolve(FILE_NAME), text.toString().toByteArray(Charsets.UTF_8))
    }

    fun consume(
        dir: Path,
        now: Long = System.currentTimeMillis(),
    ): RunRequest {
        val path = dir.resolve(FILE_NAME)
        val lines = try {
            Files.readAllLines(path)
        } catch (_: IOException) {
            return RunRequest.NONE
        }
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            return parse(lines, now)
        }
        return parse(lines, now)
    }

    fun parse(lines: List<String>, now: Long): RunRequest {
        val values = HashMap<String, String>()
        for (line in lines) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
        }
        val timestamp = values["timestamp"]?.toLongOrNull() ?: return RunRequest.NONE
        if (now - timestamp > MAX_AGE_MILLIS) return RunRequest.NONE
        return RunRequest(
            formatting = values["formatting"] == "true",
            debugPerformance = values["debugPerformance"] == "true",
        )
    }
}

class RunRequest(val formatting: Boolean, val debugPerformance: Boolean) {
    companion object {
        val NONE = RunRequest(formatting = false, debugPerformance = false)
    }
}
