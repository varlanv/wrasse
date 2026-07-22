package sample

fun f(x: String?) {
    val denulled = x ?: throw IllegalStateException("a")
    val int = x?.toInt() ?: throw IllegalStateException("b")
    val double = x?.toDouble() ?: throw IllegalStateException("c")
}

// expect-error 3:5 throws-count "Function 'f' has 3 throw statements; the maximum allowed is 2"
