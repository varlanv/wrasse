package sample

fun foo(flag: Boolean): String {
    return if (flag) {
        "a"
    } else {
        return "b"
    }
}

// expect-clean
