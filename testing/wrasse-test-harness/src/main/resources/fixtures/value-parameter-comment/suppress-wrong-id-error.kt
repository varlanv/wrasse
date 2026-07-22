package sample

@Suppress("no-semicolons")
class Foo(val bar: /* raw */ Int)

// expect-error 4:20 value-parameter-comment "A comment inside or on the same line after a value parameter is not allowed. Place it on a separate line above."
