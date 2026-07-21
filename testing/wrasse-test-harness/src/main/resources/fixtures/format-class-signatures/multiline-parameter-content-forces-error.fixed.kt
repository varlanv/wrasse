package sample

class Runner(
    val action: () -> Unit = {
        println("a")
        println("b")
    },
)