package sample

fun example(): Any {
    val x = object {
        override fun toString() = "x"
    }
    return x
}

// expect-clean
