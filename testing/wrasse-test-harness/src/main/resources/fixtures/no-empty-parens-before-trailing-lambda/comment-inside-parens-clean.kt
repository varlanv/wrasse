package sample

fun greet(f: () -> Unit) {}

fun main() {
    greet(/* x */) { }
}

// expect-clean
