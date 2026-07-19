package sample

import sample.auxenum.Status.*
// fixture-aux-file: aux/AuxEnum.kt

val s = ACTIVE
val t = INACTIVE

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
