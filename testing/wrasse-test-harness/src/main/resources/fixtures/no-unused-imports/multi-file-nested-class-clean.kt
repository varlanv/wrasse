package sample

import sample.aux.Outer

// fixture-aux-file: aux/Aux.kt

val x: Outer.Nested? = null

// expect-clean
