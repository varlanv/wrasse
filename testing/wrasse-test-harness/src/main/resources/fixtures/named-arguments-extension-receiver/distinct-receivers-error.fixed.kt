package sample

fun String.fmt(a: Int, b: Int): String = "$this:$a:$b"

fun Int.fmt(b: Int, a: Int): String = "$this:$a:$b"

fun demo(): String = "x".fmt(a = 1, b = 2)