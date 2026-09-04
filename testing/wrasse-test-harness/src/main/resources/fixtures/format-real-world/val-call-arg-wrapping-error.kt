package sample

fun union(vararg lists: List<String>): List<String> = lists.flatMap { it }
fun lastParensTicker(s: String): String? = s.substringAfterLast('(').substringBeforeLast(')').takeIf { it.isNotEmpty() }
fun spotListingBodyAssets(s: String): List<String>? = s.split(",").takeIf { it.isNotEmpty() }

fun extractAssets(title: String, fullArticle: String): List<String> {
    val assets = union(
        listOfNotNull(lastParensTicker(title)), spotListingBodyAssets(fullArticle) ?: listOf())
    return assets
}

// expect-error 1:1 format "File is not wrasse-formatted"
