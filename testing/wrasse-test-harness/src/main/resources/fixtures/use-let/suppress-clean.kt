package sample

@Suppress("use-let")
fun check(y: Int?): Int? {
    return if (y == null) null else y
}

// expect-clean
