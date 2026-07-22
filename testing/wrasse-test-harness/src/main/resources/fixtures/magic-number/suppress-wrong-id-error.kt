package sample

fun foo(x: Int) {
}

@Suppress("no-semicolons")
fun run() {
    foo(42)
}

// expect-error 8:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
