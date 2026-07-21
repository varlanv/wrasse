package sample

annotation class WithArg(val v: String)

@WithArg("x")
fun a() {}