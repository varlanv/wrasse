package sample

fun foo(bar: Int) = bar

@Suppress("value-argument-comment")
val result = foo(bar /* raw */ = 1)

// expect-clean
