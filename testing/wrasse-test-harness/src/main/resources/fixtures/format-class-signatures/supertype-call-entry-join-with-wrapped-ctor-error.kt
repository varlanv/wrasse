package sample

open class Base(x: Int, y: Int)

class Foo(a: Int, b: Int, c: Int) : Base(a, b)

// expect-error 1:1 format "File is not wrasse-formatted"
