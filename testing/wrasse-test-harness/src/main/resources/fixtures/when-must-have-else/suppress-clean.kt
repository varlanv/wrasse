package sample

@Suppress("when-must-have-else")
fun foo(x: Int) {
    when (x) {
        1 -> println("one")
        2 -> println("two")
    }
}

// expect-clean
