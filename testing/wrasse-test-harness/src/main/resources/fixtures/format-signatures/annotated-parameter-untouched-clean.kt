package sample

annotation class Bar

fun f(@Bar a: Int, b: Int) {}

// expect-clean
