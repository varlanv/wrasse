package sample

import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt

val w = Widget()
val g: sample.aux.Gadget = sample.aux.Gadget()

// expect-error 6:8 no-unnecessary-fqn "Unnecessary fully qualified name"
