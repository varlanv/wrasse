package sample

import sample.auxbase.Vec
import sample.auxop.*
// fixture-aux-file: aux/AuxBase.kt
// fixture-aux-file: aux/AuxOp.kt

val a = Vec(1, 2)
val b = Vec(3, 4)
val c = a + b

// expect-clean
