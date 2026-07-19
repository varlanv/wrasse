package sample

import sample.aux.Alpha
import sample.aux.Unused
// fixture-aux-file: aux/Aux.kt

val a = Alpha()
val z: sample.aux.Zebra = TODO()

// expect-error 4:1 no-unused-imports "Unused import"
// expect-error 7:8 no-unnecessary-fqn "Unnecessary fully qualified name"