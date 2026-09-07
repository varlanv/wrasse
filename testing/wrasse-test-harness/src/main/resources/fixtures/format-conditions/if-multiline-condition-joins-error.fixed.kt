package sample

fun visible(paths: List<String>, path: String): Boolean {
    if (paths.none { it == path } && paths.any { it.startsWith(path) }) {
        return false
    }
    return true
}