package sample

annotation class WithArg(val v: String)

fun bar() {}

fun run() {
    @WithArg("x") bar()
}

// expect-error 1:1 format "File is not wrasse-formatted"
