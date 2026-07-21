package sample

annotation class Ann

class Foo @Ann constructor(x: Int)

// expect-clean
