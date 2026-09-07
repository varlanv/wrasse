package sample

fun toGlob(path: String, rootDir: String): List<String> = listOf(path + rootDir)

fun String.expandTildeToFullPath(): String = this

fun globs(patterns: List<String>, rootDir: String): List<String> {
    val result = patterns.mapNotNull { pattern ->
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) null else trimmed
    }.map { it.expandTildeToFullPath() }.flatMap { path -> toGlob(path, rootDir) }
    return result
}

// expect-error 1:1 format "File is not wrasse-formatted"
