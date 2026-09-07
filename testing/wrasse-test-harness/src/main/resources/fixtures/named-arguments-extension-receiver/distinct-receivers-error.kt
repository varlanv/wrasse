package sample

fun String.fmt(a: Int, b: Int): String = "$this:$a:$b"

fun Int.fmt(b: Int, a: Int): String = "$this:$a:$b"

fun demo(): String = "x".fmt(1, 2)

// expect-error 7:29 named-arguments "Positional arguments should be named"
