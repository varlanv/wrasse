package sample

class Runner(val action: () -> Unit = {
    println("a")
    println("b")
})

// expect-error 1:1 format "File is not wrasse-formatted"
