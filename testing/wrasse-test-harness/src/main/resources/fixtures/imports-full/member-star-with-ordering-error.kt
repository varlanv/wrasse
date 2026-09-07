package sample

import sample.auxenum.Status.*
import sample.aux.Widget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxEnum.kt

val w = Widget()
val s = ACTIVE

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 3:1 import-ordering "Imports are not sorted"
