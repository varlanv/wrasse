package sample

import sample.overload.a.pick
import sample.overload.b.pick

// fixture-aux-file: aux/OverloadA.kt
// fixture-aux-file: aux/OverloadB.kt

val x = pick()

// expect-error 4:1 no-unused-imports "Unused import"
