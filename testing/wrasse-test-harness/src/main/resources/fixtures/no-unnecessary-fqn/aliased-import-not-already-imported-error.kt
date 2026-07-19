package sample

import sample.aux.Widget as W
// fixture-aux-file: aux/Aux.kt

val aliased: W = TODO()
val w: sample.aux.Widget = TODO()

// expect-error 6:8 no-unnecessary-fqn "Unnecessary fully qualified name"
