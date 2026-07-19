package sample

fun <T> foo(): Unit {}

// expect-error 3:14 no-unit-return "Redundant Unit return type"
