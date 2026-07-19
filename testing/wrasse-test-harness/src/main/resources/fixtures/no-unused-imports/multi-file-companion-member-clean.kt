package sample

import sample.aux.Outer

// fixture-aux-file: aux/Aux.kt

val x: Int = Outer.value

// expect-clean
