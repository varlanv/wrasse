package sample

import sample.auxenum.Status.*
// fixture-aux-file: aux/AuxEnum.kt

val active = ACTIVE
val all = listOf(ACTIVE, INACTIVE)

// expect-clean
