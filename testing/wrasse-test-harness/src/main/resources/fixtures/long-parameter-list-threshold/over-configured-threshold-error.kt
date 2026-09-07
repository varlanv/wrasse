package sample

class Foo(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int)

fun f(a: Int, b: Int, c: Int, d: Int) {
}

// expect-error 3:10 long-parameter-list "The constructor has 5 parameters; the maximum allowed is 4"
// expect-error 5:6 long-parameter-list "The function has 4 parameters; the maximum allowed is 3"
