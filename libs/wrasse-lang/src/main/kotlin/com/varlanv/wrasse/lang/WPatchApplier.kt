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
    fun apply(patchDir: Path): ApplyResult {
        if (!Files.exists(patchDir)) return ApplyResult(emptyList())

        val patchFiles = Files.walk(patchDir).use { walk ->
            walk
                .asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString() == WPatchStore.PATCH_FILE_NAME }
                .toList()
        }

        val results = mutableListOf<FileApplyResult>()
        for (patchFile in patchFiles) {
            val allEdits = WPatchReader.read(Files.readString(patchFile))
            for (fileEdits in allEdits) {
                val filePath = Path.of(fileEdits.filePath)
                results.add(applyToFile(filePath, fileEdits))
            }
        }

        return ApplyResult(results)
    }

    private fun applyToFile(filePath: Path, fileEdits: FileEdits): FileApplyResult {
        val bytes =
            try {
                Files.readAllBytes(filePath)
            } catch (_: NoSuchFileException) {
                return FileApplyResult.Skipped(filePath, "file not found")
            }

        if (Sha256.ofBytes(bytes) != fileEdits.sourceHash) {
            val content = String(bytes, Charsets.UTF_8)
            val reason =
                if (Sha256.ofText(content.replace("\r\n", "\n")) == fileEdits.sourceHash) {
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

        return FileApplyResult.Applied(filePath, ascending.size)
    }

    fun sha256(content: String): String = Sha256.ofText(content)
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: WPatchApplier <patchDir> [<patchDir>...]" }
    for (arg in args) {
        val result = WPatchApplier.apply(Path.of(arg))
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
                    kotlin.system.exitProcess(1)
                }
            }
        }
    }
}

class ApplyResult(val files: List<FileApplyResult>)

sealed class FileApplyResult {
    abstract val filePath: Path

    class Applied(override val filePath: Path, val editCount: Int) : FileApplyResult()

    class Skipped(override val filePath: Path, val reason: String) : FileApplyResult()

    class Failed(override val filePath: Path, val reason: String) : FileApplyResult()
}
