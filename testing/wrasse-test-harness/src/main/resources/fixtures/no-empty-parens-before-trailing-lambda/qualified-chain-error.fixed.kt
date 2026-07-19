package sample

object A {
    object B {
        fun greet(f: () -> Unit) {}
    }
}

fun main() {
    A.B.greet { }
}