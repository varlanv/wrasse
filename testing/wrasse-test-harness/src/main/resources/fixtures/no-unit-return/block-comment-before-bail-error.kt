package sample

fun foo(): /* keep */ Unit {}

// expect-error 3:10 no-unit-return "Redundant Unit return type (no autofix for this shape)"
