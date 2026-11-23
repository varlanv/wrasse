package sample

fun runAll(vararg a: () -> Int) = 1

// wrasse matrix:2
val n = runAll({ 1 }, { 2 }) { 3 }

// expect-error 5:1 format-matrix "'// wrasse matrix:2' does not support a call with a trailing lambda"
