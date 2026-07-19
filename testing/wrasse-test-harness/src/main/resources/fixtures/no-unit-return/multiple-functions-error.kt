package sample

fun a(): Unit {}
fun b(): Unit {}

// expect-error 3:8 no-unit-return "Redundant Unit return type"
// expect-error 4:8 no-unit-return "Redundant Unit return type"
