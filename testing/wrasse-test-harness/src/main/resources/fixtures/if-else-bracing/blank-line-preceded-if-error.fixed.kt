package sample

fun test(): Int {
    val b = foo()

    if (b) {
        return 1
    } else {
        return 2
    }
}

fun foo() = true