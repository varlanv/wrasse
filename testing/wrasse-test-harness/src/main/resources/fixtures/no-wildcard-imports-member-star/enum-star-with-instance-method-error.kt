package sample

import sample.auxenum.Status.*
// fixture-aux-file: aux/AuxEnum.kt

val s = ACTIVE
val d = s.describe()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
