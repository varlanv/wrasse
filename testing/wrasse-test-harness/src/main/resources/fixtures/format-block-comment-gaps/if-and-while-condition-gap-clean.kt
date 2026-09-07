package sample

fun guarded(flag: Boolean): Int {
    if (/* why */ flag) {
        return 1
    }
    while (flag /* still */) {
        return 2
    }
    return 0
}

// expect-clean
