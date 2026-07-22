package sample

fun run() {
    println(if (5 < 6) 7 else 8)
}

// expect-error 4:17 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 4:21 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 4:24 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 4:31 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
