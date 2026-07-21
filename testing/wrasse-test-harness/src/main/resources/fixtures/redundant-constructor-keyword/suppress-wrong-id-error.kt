package sample

@Suppress("no-such-rule")
class Foo constructor(x: Int)

// expect-error 4:11 redundant-constructor-keyword "Redundant constructor keyword"
