package sample

fun demo() {
    val pair = "a" to "b"
    val (first, second,) = pair
    println(first + second)
}

// expect-error 1:1 format "File is not wrasse-formatted"
