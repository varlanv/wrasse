package sample

annotation class Marker

fun outer() {
    val bar = "bar"
    @Marker
    val foo = "foo"
}

// expect-error 1:1 format "File is not wrasse-formatted"
