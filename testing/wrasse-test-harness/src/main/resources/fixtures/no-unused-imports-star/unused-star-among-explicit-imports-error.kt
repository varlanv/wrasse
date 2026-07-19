package sample

import sample.aux.Widget
import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()

// expect-error 4:1 no-unused-imports "Unused import"
