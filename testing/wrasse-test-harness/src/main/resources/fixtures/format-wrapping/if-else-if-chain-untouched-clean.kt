package sample

fun one() {}

fun two() {}

fun chainCase(a: Boolean, b: Boolean) {
    if (a) {
        one()
    } else if (b) {
        two()
    } else {
        one()
        two()
    }
}

// expect-clean
