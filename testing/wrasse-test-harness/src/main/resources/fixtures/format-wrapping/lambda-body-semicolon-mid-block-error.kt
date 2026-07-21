package sample

fun example() {
    val f = { x: Int ->
        print(x); print(x)
    }
    f(1)
}

// expect-error 1:1 format "File is not wrasse-formatted"
