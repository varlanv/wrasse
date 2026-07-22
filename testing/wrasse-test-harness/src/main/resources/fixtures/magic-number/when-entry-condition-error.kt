package sample

fun test(x: Int): Int {
    when (x) {
        5 -> return 5
        4 -> return 4
        3 -> return 3
    }
    return 0
}

// expect-error 5:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 6:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 7:9 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
