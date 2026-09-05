package sample

class Inner(val a: Int, val b: Int)

class Outer(val inner: Inner, val label: String)

fun build(): Outer = Outer(Inner(1, 2), "label")

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 7:27 named-arguments "Positional arguments should be named"
// expect-error 7:33 named-arguments "Positional arguments should be named"
