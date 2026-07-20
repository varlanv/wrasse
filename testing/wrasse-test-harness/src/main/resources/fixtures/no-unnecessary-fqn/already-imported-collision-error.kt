package sample

import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxOther.kt

val w: sample.aux.Widget = TODO()
val other: sample.aux2.Widget = TODO()

// expect-error 5:8 no-unnecessary-fqn "Unnecessary fully qualified name"
