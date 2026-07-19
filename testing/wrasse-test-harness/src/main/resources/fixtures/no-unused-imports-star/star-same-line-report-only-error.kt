package sample

import sample.aux.Widget; import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()

// expect-error 3:27 no-unused-imports "Unused import"
