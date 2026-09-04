package sample

fun run(block: () -> Unit) {
    block()
}

fun main() {
    run({
        println("a")
        println("b")
    })
}

// expect-clean
