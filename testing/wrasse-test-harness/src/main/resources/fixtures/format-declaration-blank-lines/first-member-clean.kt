package sample

class Box {
    val x = 1
}

fun outer() {
    fun inner() {}
    inner()
}

// expect-clean
