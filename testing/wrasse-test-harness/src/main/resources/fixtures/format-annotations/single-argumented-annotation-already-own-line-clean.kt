package sample

annotation class WithArg(val v: Int)

@WithArg(1)
fun a() {}

// expect-clean
