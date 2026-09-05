package sample

class Inner(val a: Int, val b: Int)

class Outer(val inner: Inner, val label: String)

fun build(): Outer = Outer(inner = Inner(a = 1, b = 2), label = "label")