package sample

fun process(action: () -> Unit = {
    println("a")
    println("b")
}) {
    action()
}

// expect-error 1:1 format "File is not wrasse-formatted"
