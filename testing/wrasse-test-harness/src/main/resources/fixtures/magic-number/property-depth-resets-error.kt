package sample

fun compute(): Int {
    val x = 5
    return x + 99
}

// expect-error 5:16 magic-number "This expression contains a magic number; consider defining it as a well-named constant"
