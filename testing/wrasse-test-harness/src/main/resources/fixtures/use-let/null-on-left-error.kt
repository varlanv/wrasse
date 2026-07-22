package sample

fun check(y: Int?): Int? {
    return if (null == y) null else y
}

// expect-error 4:12 use-let "Use '?.let { }' instead of this if/else null check"
