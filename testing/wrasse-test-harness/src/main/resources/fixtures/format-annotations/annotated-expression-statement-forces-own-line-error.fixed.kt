package sample

annotation class WithArg(val v: String)

fun bar() {}

fun run() {
    @WithArg("x")
    bar()
}