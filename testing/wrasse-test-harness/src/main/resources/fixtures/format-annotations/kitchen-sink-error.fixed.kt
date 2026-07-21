package sample

annotation class Ann1

annotation class Ann2

annotation class WithArg(val v: String)

@WithArg("x")
fun a() {}

@Ann1
@Ann2
fun b() {}

fun c(@WithArg("y") d: Int) {}