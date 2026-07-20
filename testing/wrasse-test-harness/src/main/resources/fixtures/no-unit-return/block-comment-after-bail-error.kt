package sample

fun foo(): Unit /* keep */ {}

// expect-error 3:10 no-unit-return "Redundant Unit return type (no autofix for this shape)"
