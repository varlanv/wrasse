package sample

import sample.aux.Widget
import sample.aux.Unused
import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget();
val g = Gadget()

// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 5:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 7:17 no-semicolons "Unnecessary semicolon"
