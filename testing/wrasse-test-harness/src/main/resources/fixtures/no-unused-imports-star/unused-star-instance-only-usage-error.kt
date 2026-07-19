package sample

import sample.aux.Helper.*
import sample.aux.makeHelper
// fixture-aux-file: aux/Aux.kt

val h = makeHelper()
val v = h.value

// expect-error 3:1 no-unused-imports "Unused import"
