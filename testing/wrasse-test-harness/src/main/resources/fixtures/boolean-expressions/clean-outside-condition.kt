package sample

fun foo(a: Boolean): Boolean {
    val flag = a && true
    return flag
}

// expect-clean
