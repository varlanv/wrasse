package sample.aux

import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
