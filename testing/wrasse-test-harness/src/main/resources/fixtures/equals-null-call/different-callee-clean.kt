package sample

fun check(str: String) = str.contentEquals(null as CharSequence?)

// expect-clean
