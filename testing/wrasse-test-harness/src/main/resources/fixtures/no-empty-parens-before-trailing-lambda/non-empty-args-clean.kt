package sample

fun greetWith(x: Int, f: () -> Unit) {}

fun main() {
    greetWith(1) { }
}

// expect-clean
