package sample

fun demo() {
    val f = { x: Int, -> x }
    println(f(1))
}

// expect-error 1:1 format "File is not wrasse-formatted"
