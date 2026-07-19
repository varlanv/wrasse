package sample

import sample.aux.*
import sample.aux.*
// fixture-aux-file: aux/Aux.kt
val x = 1

// expect-error 3:1 no-unused-imports "Unused import"
// expect-error 4:1 no-unused-imports "Unused import"
