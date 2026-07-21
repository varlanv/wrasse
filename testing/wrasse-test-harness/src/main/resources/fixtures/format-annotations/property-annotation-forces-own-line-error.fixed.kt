package sample

annotation class WithArg(val v: String)

@WithArg("x")
val a: Int = 1