package sample

annotation class WithArg(val v: String)

fun a(@WithArg("x") b: Int) {}

// expect-clean
