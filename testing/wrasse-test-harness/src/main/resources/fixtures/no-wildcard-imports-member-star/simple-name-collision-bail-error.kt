package sample

import sample.auxenum.Status.*
// fixture-aux-file: aux/AuxEnum.kt
// fixture-aux-file: aux/AuxOther.kt

val s = ACTIVE
val o = sample.auxother.ACTIVE

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
