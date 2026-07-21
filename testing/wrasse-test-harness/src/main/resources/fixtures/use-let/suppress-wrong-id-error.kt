package sample

@Suppress("no-semicolons")
fun check(y: Int?): Int? {
    return if (y == null) null else y
}

// expect-error 5:12 use-let "Use '?.let { }' instead of this if/else null check"
