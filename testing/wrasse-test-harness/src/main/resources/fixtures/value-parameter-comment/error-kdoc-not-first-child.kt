package sample

class Foo(val bar: /** doc */ Int)

// expect-error 3:20 value-parameter-comment "A comment inside or on the same line after a value parameter is not allowed. Place it on a separate line above."
