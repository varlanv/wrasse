package sample

annotation class Ann1

annotation class Ann2

@[Ann1] @Ann2
fun a() {}

// expect-clean
