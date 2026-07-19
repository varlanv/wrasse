package sample

class Box(val f: () -> Unit)

fun main() {
    Box { }
}