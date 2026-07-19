package sample

import sample.aux.Gadget
import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxSorted.kt

val g = Gadget()
val w = Widget()
val m: sample.aux.Middle = TODO()

// expect-error 8:8 no-unnecessary-fqn "Unnecessary fully qualified name"