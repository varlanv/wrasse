package sample

fun foo(x: UInt) {
}

fun run() {
    foo(65520U)
}

// expect-error 7:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
