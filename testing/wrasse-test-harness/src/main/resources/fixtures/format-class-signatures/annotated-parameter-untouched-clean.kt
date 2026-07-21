package sample

annotation class Bar

class Foo(@Bar a: Int, b: Int)

// expect-clean
