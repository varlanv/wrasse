package sample

fun f(a: Int): String {
    if (a == 1) {
        return "one"
    } else if (a == 2) {
        return "two"
    } else if (a == 3) {
        return "three"
    } else if (a == 4) {
        return "four"
    } else if (a == 5) {
        return "five"
    } else {
        return "other"
    }
}

// expect-clean
