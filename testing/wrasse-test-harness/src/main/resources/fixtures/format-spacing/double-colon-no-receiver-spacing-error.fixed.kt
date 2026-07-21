package sample

fun isPositive(value: Int): Boolean = value > 0

val predicate: (Int) -> Boolean = ::isPositive