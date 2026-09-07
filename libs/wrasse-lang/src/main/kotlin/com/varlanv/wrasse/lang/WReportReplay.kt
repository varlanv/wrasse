package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * Replays the diagnostics recorded under a `build/wrasse` tree in the form kotlinc prints them,
 * so a build tool can show them again for a compile that did not run. An entry whose source
 * hash no longer matches the file on disk is stale (the file changed since that compile) and is
 * left out, as is one whose file is gone.
 */
object WReportReplay {
    fun collect(wrasseDir: Path): List<ReportedFile> {
        if (!Files.isDirectory(wrasseDir)) return emptyList()
        val result = ArrayList<ReportedFile>()
        Files.walk(wrasseDir).use { paths ->
            paths
                .filter { it.fileName.toString() == WReportStore.REPORT_FILE_NAME && Files.isRegularFile(it) }
                .sorted()
                .forEach { reportFile ->
                    for (entry in WReportReader.read(Files.readString(reportFile))) {
                        if (entry.diagnostics.isNotEmpty() && isCurrent(entry)) result.add(entry)
                    }
                }
        }
        return result
    }

    fun render(file: ReportedFile, diagnostic: ReportedDiagnostic): String {
        val prefix = if (diagnostic.isError) "e" else "w"
        return "$prefix: ${Path
            .of(file.filePath)
            .toUri()}:${diagnostic.line}:${diagnostic.column} wrasse: ${diagnostic.message}"
    }

    private fun isCurrent(entry: ReportedFile): Boolean {
        val text = try {
            Files.readString(Path.of(entry.filePath))
        } catch (_: NoSuchFileException) {
            return false
        }
        return Sha256.ofText(text) == entry.sourceHash
    }
}

/**
 * Every current diagnostic under [wrasseDirs], rendered one per line; the entry point a build tool
 * calls in-process. Lines starting with `e: ` are error-level findings.
 */
fun replayReports(wrasseDirs: List<String>): List<String> {
    val lines = ArrayList<String>()
    for (dir in wrasseDirs) {
        for (file in WReportReplay.collect(Path.of(dir))) {
            for (diagnostic in file.diagnostics) lines.add(WReportReplay.render(file, diagnostic))
        }
    }
    return lines
}
