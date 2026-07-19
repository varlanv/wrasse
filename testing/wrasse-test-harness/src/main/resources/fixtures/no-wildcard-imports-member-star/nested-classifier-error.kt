package sample

import sample.auxclass.Container.*
// fixture-aux-file: aux/AuxClass.kt

val n = Nested()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
