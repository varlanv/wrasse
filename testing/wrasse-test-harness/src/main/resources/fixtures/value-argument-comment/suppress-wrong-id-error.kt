package sample

fun foo(bar: Int) = bar

@Suppress("no-semicolons")
val result = foo(bar /* raw */ = 1)

// expect-error 6:22 value-argument-comment "A comment inside or on the same line after a value argument is not allowed. Place it on a separate line above."
