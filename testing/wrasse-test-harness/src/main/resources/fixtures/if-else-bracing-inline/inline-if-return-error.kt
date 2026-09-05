package sample

fun guard(condition: Boolean) {
    if (condition) return
    println("continue")
}

// expect-error 4:20 if-else-bracing "Missing braces on branch of if-statement"
