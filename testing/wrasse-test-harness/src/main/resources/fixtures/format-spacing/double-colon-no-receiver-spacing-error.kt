package sample

fun isPositive(value: Int): Boolean = value > 0

val predicate: (Int) -> Boolean = :: isPositive

// expect-error 1:1 format "File is not wrasse-formatted"
