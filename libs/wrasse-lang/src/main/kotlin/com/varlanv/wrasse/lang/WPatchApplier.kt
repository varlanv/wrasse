package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.streams.asSequence

/**
 * Host B: applies every `wrasse-fixes.txt` under a directory to the files they name. Each source
 * file is read once as bytes, hashed as read (the plugin hashed the same UTF-8 bytes from its
 * text), decoded once, and written through a buffered writer as untouched segments interleaved
 * with replacements, so no second copy of the content is ever built. Edits are applied in
 * ascending order; among insertions at one offset the last one listed lands leftmost, matching the
 * order a descending in-place application produced.
 */
object WPatchApplier {
    fun apply(
        patchDir: Path,
        perf: WPerf = NoopPerf,
    ): ApplyResult {
        if (!Files.exists(patchDir)) return ApplyResult(emptyList())
        val started = if (perf.enabled) System.nanoTime() else 0L

        val files = Files.walk(patchDir).use { walk -> walk.asSequence().filter { Files.isRegularFile(it) }.toList() }
        for (file in files) {
            if (file.fileName.toString() == FormatRequest.FILE_NAME) Files.deleteIfExists(file)
        }
        val patchFiles = files.filter { it.fileName.toString() == WPatchStore.PATCH_FILE_NAME }

        val results = mutableListOf<FileApplyResult>()
        for (patchFile in patchFiles) {
            val readStarted = if (perf.enabled) System.nanoTime() else 0L
            val allEdits = WPatchReader.read(Files.readString(patchFile))
            if (perf.enabled) perf.record("apply:read-journal", System.nanoTime() - readStarted)
            for (fileEdits in allEdits) {
                val filePath = Path.of(fileEdits.filePath)
                val fileStarted = if (perf.enabled) System.nanoTime() else 0L
                val result = applyToFile(filePath, fileEdits, perf)
                results.add(result)
                if (perf.enabled) {
                    perf.record("file:$filePath", System.nanoTime() - fileStarted)
                    perf.add("count:files", 1)
                    perf.add("count:edits", fileEdits.edits.size.toLong())
                }
            }
        }

        if (perf.enabled) perf.record("apply:total", System.nanoTime() - started)
        return ApplyResult(results)
    }

    private fun applyToFile(
        filePath: Path,
        fileEdits: FileEdits,
        perf: WPerf,
    ): FileApplyResult {
        val readStarted = if (perf.enabled) System.nanoTime() else 0L
        val bytes = try {
            Files.readAllBytes(filePath)
        } catch (_: NoSuchFileException) {
            return FileApplyResult.Skipped(filePath, "file not found")
        }
        if (perf.enabled) {
            perf.record("apply:read-source", System.nanoTime() - readStarted)
            perf.add("count:bytes-read", bytes.size.toLong())
        }

        val hashStarted = if (perf.enabled) System.nanoTime() else 0L
        val hashMatches = Sha256.ofBytes(bytes) == fileEdits.sourceHash
        if (perf.enabled) perf.record("apply:hash", System.nanoTime() - hashStarted)
        if (!hashMatches) {
            val content = String(bytes, Charsets.UTF_8)
            val reason = if (Sha256.ofText(content.replace("\r\n", "\n")) == fileEdits.sourceHash) {
                "source line endings differ from what the compiler analyzed (CRLF vs LF); re-run the build to refresh the patch"
            } else {
                "source changed since compilation"
            }
            return FileApplyResult.Skipped(filePath, reason)
        }

        val ascending = fileEdits.edits.asReversed().sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
        for (i in 0 until ascending.size - 1) {
            val current = ascending[i]
            val next = ascending[i + 1]
            if (next.startOffset < current.endOffset) {
                return FileApplyResult.Failed(
                    filePath,
                    "overlapping edits at ${current.startOffset}..${current.endOffset} and ${next.startOffset}..${next.endOffset}",
                )
            }
        }

        val writeStarted = if (perf.enabled) System.nanoTime() else 0L
        val content = String(bytes, Charsets.UTF_8)
        val tmpFile = filePath.resolveSibling(filePath.fileName.toString() + ".wrasse-tmp")
        try {
            Files.newBufferedWriter(tmpFile).use { out ->
                var position = 0
                for (edit in ascending) {
                    out.append(content, position, edit.startOffset)
                    out.append(edit.replacement)
                    position = edit.endOffset
                }
                out.append(content, position, content.length)
            }
            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            throw e
        }
        if (perf.enabled) perf.record("apply:splice-and-write", System.nanoTime() - writeStarted)

        return FileApplyResult.Applied(filePath, ascending.size)
    }

    fun sha256(content: String): String = Sha256.ofText(content)
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: WPatchApplier <patchDir> [<patchDir>...]" }
    if (runApplier(args.toList()) != 0) kotlin.system.exitProcess(1)
}

/**
 * Applies every patch under each of [patchDirs], printing one line per file, and returns the
 * process exit code `main` would end with: 0, or 1 after the first file that failed to apply.
 * Safe to call in-process: it never exits the JVM.
 */
fun runApplier(patchDirs: List<String>): Int {
    val compileReports = patchDirs.flatMap { PerfStore.collect(Path.of(it)) }
    for ((title, recorder) in compileReports) print(PerfReport.render(title, recorder))
    val perf = WPerf.create(active = compileReports.isNotEmpty())
    for (dir in patchDirs) {
        val result = WPatchApplier.apply(Path.of(dir), perf)
        for (fileResult in result.files) {
            when (fileResult) {
                is FileApplyResult.Applied -> {
                    println("Fixed: ${fileResult.filePath} (${fileResult.editCount} edits)")
                }
                is FileApplyResult.Skipped -> {
                    println("Skipped: ${fileResult.filePath} (${fileResult.reason})")
                }
                is FileApplyResult.Failed -> {
                    System.err.println("FAILED: ${fileResult.filePath} - ${fileResult.reason}")
                    return 1
                }
            }
        }
    }
    if (perf is PerfRecorder) print(PerfReport.render("apply", perf))
    return 0
}

class ApplyResult(val files: List<FileApplyResult>)

sealed class FileApplyResult {
    abstract val filePath: Path

    class Applied(override val filePath: Path, val editCount: Int) : FileApplyResult()

    class Skipped(override val filePath: Path, val reason: String) : FileApplyResult()

    class Failed(override val filePath: Path, val reason: String) : FileApplyResult()
}
