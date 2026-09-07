package sample

import sample.aux.Widget
import sample.aux.Unused
import sample.aux.*
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxOther2.kt

val w = Widget();
val g = Gadget()
val e: sample.other.Extra = TODO()

// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 5:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 3:1 import-ordering "Imports are not sorted"
// expect-error 7:17 no-semicolons "Unnecessary semicolon"
// expect-error 9:8 no-unnecessary-fqn "Unnecessary fully qualified name"