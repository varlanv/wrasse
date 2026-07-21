package sample

class Foo(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int, val g: Int, val h: Int)

// expect-error 3:10 long-parameter-list "The constructor has 7 parameters; the maximum allowed is 6"
