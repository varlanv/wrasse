package sample

fun reachable(excludedPathPatterns: List<String>, candidatePath: String): Boolean {
    if (
        excludedPathPatterns.none { it == candidatePath } &&
        excludedPathPatterns.any { candidatePath.startsWith(it) } &&
        candidatePath.isNotEmpty()
    ) {
        return false
    }
    return true
}

// expect-error 1:1 format "File is not wrasse-formatted"
