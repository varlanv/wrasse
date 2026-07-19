package sample

import sample.auxop.*
// fixture-aux-file: aux/AuxOp.kt

val a = Vec(1, 2)
val b = Vec(3, 4)
val c = a + b

fun run() {
    val (px, py) = a
}

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
