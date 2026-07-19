package sample

fun greet(f: () -> Unit) {}

@Suppress("no-empty-parens-before-trailing-lambda")
fun main() {
    greet() { }
}

// expect-clean
