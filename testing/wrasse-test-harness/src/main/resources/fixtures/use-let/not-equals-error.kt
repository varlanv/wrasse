package sample

fun transform(x: Int): Int = x

fun check(x: Int?): Int? {
    return if (x != null) { transform(x) } else null
}

// expect-error 6:12 use-let "Use '?.let { }' instead of this if/else null check"
