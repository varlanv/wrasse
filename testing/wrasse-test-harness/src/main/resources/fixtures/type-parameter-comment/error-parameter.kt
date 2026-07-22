package sample

class Foo<in /* raw */ T>

// expect-error 3:14 type-parameter-comment "A comment inside or on the same line after a type parameter is not allowed. Place it on a separate line above."
