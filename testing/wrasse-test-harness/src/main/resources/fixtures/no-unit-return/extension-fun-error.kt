package sample

fun String.foo(): Unit {}

// expect-error 3:17 no-unit-return "Redundant Unit return type"
