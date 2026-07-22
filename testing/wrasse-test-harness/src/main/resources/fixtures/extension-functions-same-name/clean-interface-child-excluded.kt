package sample

interface Base
interface Derived : Base

fun Base.process(x: Int): String = "base"
fun Derived.process(x: Int): String = "derived"

// expect-clean
