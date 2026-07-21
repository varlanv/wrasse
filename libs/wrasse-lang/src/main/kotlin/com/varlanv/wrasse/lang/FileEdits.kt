package com.varlanv.wrasse.lang

/**
 * Accumulated edits for one source file, ready to be written to a patch file.
 *
 * [sourceHash] is the SHA-256 hex digest of the file's content at compilation time.
 * The apply step uses it to skip stale patches when the file has changed since compilation.
 */
class FileEdits(val filePath: String, val sourceHash: String, val edits: List<WEdit>)
