package sample

fun x() = 9 + 1

fun y(): Int {
    return 9 + 1
}

// expect-error 3:11 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
// expect-error 6:12 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
