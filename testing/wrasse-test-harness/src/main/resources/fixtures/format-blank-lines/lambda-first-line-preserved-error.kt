package sample

fun demo() {
    val block = {

        println("hi")
    }
    block()


    block()
}

// expect-error 1:1 format "File is not wrasse-formatted"
