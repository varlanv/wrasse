package sample

fun classify(x: Int) {
    when (x) {
        1 -> {}
        2 ->
            println("two")
        else -> println("other")
    }
}

// expect-clean