package sample

fun demo() {
    val combine = { a: Int,
        b: Int ->
        a + b
    }
    println(combine(1, 2))
}

// expect-error 1:1 format "File is not wrasse-formatted"
