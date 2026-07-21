package sample

annotation class Ann1

annotation class Ann2

annotation class WithArg(val v: String)

@Ann1 fun a() {}

@WithArg("x")
fun b() {}

@Ann1
@Ann2
fun c() {}

fun d(@WithArg("y") e: Int) {}

// expect-clean
