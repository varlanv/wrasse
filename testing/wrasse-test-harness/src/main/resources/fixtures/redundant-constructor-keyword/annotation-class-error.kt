package sample

annotation class Foo constructor(val foo: Int)

// expect-error 3:22 redundant-constructor-keyword "Redundant constructor keyword"
