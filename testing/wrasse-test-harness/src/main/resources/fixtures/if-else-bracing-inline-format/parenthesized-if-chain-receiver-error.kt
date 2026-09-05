package sample

class Bar(val openTime: Long)

fun pick(
    fallback: Boolean,
    primary: List<Bar>?,
    secondary: List<Bar>?,
): List<Bar> {
    val seconds = (if (fallback) primary else secondary)?.sortedBy { it.openTime }?.distinctBy { it.openTime }.orEmpty()
    return seconds
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 10:34 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 10:47 if-else-bracing "Missing braces on branch of if-statement"
