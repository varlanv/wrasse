package sample

import sample.aux.*
// keep me
import sample.aux.Unused
import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt

val w = Widget()
val g = Gadget()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 5:1 no-unused-imports "Unused import"
