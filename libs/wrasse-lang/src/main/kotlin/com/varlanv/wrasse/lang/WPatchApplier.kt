package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Reads a wrasse patch file and applies the edits to source files on disk.
 *
 * For each file in the patch:
 * 1. Validates the source hash — skips if the file changed since compilation.
 * 2. Checks for overlapping edits — fails loudly (this is a rule design bug).
 * 3. Applies edits in descending offset order so earlier edits don't shift later offsets.
 * 4. Writes via temp file + atomic rename.
 *
 * Deletes the patch file after all edits are applied.
 */
object WPatchApplier {

    private const val PATCH_FILE_NAME = "wrasse-fixes.txt"

    fun apply(patchDir: Path): ApplyResult {
        val patchFile = patchDir.resolve(PATCH_FILE_NAME)
        if (!Files.exists(patchFile)) return ApplyResult(emptyList())

        val allEdits = WPatchReader.read(Files.readString(patchFile))
        val results = mutableListOf<FileApplyResult>()

        for (fileEdits in allEdits) {
            val filePath = Path.of(fileEdits.filePath)
            results.add(applyToFile(filePath, fileEdits))
        }

        Files.delete(patchFile)
        return ApplyResult(results)
    }

    private fun applyToFile(filePath: Path, fileEdits: FileEdits): FileApplyResult {
        if (!Files.exists(filePath)) {
            return FileApplyResult.Skipped(filePath, "file not found")
        }

        val content = Files.readString(filePath)
        val currentHash = sha256(content)
        if (currentHash != fileEdits.sourceHash) {
            val reason = if (sha256(content.replace("\r\n", "\n")) == fileEdits.sourceHash) {
                "source line endings differ from what the compiler analyzed (CRLF vs LF); re-run the build to refresh the patch"
            } else {
                "source changed since compilation"
            }
            return FileApplyResult.Skipped(filePath, reason)
        }

        val sorted = fileEdits.edits.sortedByDescending { it.startOffset }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            if (next.endOffset > current.startOffset) {
                return FileApplyResult.Failed(
                    filePath,
                    "overlapping edits at ${next.startOffset}..${next.endOffset} and ${current.startOffset}..${current.endOffset}"
                )
            }
        }

        val sb = StringBuilder(content)
        for (edit in sorted) {
            sb.replace(edit.startOffset, edit.endOffset, edit.replacement)
        }

        val tmpFile = filePath.resolveSibling(filePath.fileName.toString() + ".wrasse-tmp")
        try {
            Files.writeString(tmpFile, sb.toString())
            Files.move(tmpFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(tmpFile)
            throw e
        }

        return FileApplyResult.Applied(filePath, sorted.size)
    }

    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return HexEncoding.lowerCase(hash)
    }
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: WPatchApplier <patchDir> [<patchDir>...]" }
    for (arg in args) {
        val result = WPatchApplier.apply(Path.of(arg))
        for (fileResult in result.files) {
            when (fileResult) {
                is FileApplyResult.Applied -> println("Fixed: ${fileResult.filePath} (${fileResult.editCount} edits)")
                is FileApplyResult.Skipped -> println("Skipped: ${fileResult.filePath} (${fileResult.reason})")
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
