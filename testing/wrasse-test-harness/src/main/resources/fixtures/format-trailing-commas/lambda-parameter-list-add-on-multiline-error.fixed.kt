package sample

fun demo() {
    val combine = { a: Int,
        b: Int, ->
        a + b
    }
    println(combine(1, 2))
}