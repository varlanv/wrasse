package sample

annotation class Ann1

fun a() {}
@Ann1
fun b() {}

// expect-error 1:1 format "File is not wrasse-formatted"
