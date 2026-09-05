package sample

fun describe(count: Int): String = "count=$count"

fun report(flag: Boolean, count: Int): String {
    val label = if (flag) describe(count) else "none"
    return describe(if (count > 0) count else 0) + label
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:27 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 6:48 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 7:36 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 7:47 if-else-bracing "Missing braces on branch of if-statement"
