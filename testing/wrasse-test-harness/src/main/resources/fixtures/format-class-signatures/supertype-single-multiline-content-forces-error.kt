package sample

open class Base(val fn: () -> Unit)

class Foo(a: Int) : Base({
    println("a")
    println("b")
})

// expect-error 1:1 format "File is not wrasse-formatted"
