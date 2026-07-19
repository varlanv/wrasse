package sample

import sample.aux.Widget
import sample.aux.Unused
// fixture-aux-file: aux/Aux.kt

val w = Widget()

// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 3:1 import-ordering "Imports are not sorted"
