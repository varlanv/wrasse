package sample

@Suppress("no-semicolons")
class Foo4<in /* raw */ T>

// expect-error 4:15 type-parameter-comment "A comment inside or on the same line after a type parameter is not allowed. Place it on a separate line above."
