package sample

fun visible(excludedPathPatterns: List<String>, candidatePath: String): Boolean {
    if (excludedPathPatterns.none { it == candidatePath } &&
        excludedPathPatterns.any { candidatePath.startsWith(it) } &&
        candidatePath.isNotEmpty()
    ) {
        return false
    }
    return true
}