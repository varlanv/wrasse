package sample

import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt

val w: sample.aux.Widget = TODO()

// expect-error 5:8 no-unnecessary-fqn "Unnecessary fully qualified name"

