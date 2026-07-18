package sample

fun foo(flag: Boolean) {
    if (flag) bar(); else baz()
}

fun bar() {}
fun baz() {}

// expect-clean
