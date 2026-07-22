package sample

fun foo(x: Int) {
}

fun run() {
    foo(42)
}

// expect-error 7:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
