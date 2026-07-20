package sample

val foo = if (isEven()) {
    0
} else System.currentTimeMillis().toInt()

fun isEven(): Boolean = System.currentTimeMillis() % 2 == 0L

// expect-error 5:8 if-else-bracing "Missing braces on branch of multi-line if-statement"
