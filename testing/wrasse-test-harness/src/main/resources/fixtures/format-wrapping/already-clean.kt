package sample

fun one() {}

fun accept(a: Any?) {}

enum class Color { RED, GREEN, BLUE }

fun blockCase() {
    if (true) {
        one()
        one()
    }
}

fun shortPropertyCase(): Int {
    val x = 1
    return x
}

fun propertyCase(cond: Boolean): Int {
    val x =
        if (cond) {
            1
        } else {
            2
        }
    return x
}

fun whenEntryCase(x: Int, cond: Boolean): Int {
    return when (x) {
        1 ->
            if (cond) {
                10
            } else {
                20
            }
        else -> 0
    }
}

fun valueArgumentCase(x: Boolean) {
    accept(
        if (x) {
            1
        } else {
            2
        },
    )
}

// expect-clean
