package sample

fun foo(bar: Int) = bar

val result = foo(bar /* raw */ = 1)

// expect-error 5:22 value-argument-comment "A comment inside or on the same line after a value argument is not allowed. Place it on a separate line above."
