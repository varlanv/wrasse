package sample

fun pick(flag: Boolean): String {
    val a = if (flag) "1" else "2"
    return a
}

// expect-error 4:23 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 4:32 if-else-bracing "Missing braces on branch of if-statement"
