package sample

fun process(
    action: () -> Unit = {
        println("a")
        println("b")
    },
) {
    action()
}