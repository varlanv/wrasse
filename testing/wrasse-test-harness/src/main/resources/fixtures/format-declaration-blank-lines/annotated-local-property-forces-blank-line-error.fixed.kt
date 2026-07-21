package sample

annotation class Marker

fun outer() {
    val bar = "bar"

    @Marker
    val foo = "foo"
}