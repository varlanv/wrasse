package sample

fun greet(f: () -> Unit) {}

fun main() {
    greet(   ) { }
}

// expect-clean
