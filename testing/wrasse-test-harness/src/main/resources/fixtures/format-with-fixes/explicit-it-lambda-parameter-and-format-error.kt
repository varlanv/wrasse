package sample

class Box {
    fun show() {
        listOf(1).forEach { it -> println(it) }
    }
}

// expect-error 5:29 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
// expect-error 1:1 format "File is not wrasse-formatted"
