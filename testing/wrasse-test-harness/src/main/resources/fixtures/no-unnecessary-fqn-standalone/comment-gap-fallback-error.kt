package sample

import sample.aux.Alpha
// about Zulu
import sample.aux.Zulu
// fixture-aux-file: aux/Aux.kt

val a = Alpha()
val z = Zulu()
val m: sample.aux.Mike = TODO()

// expect-error 9:8 no-unnecessary-fqn "Unnecessary fully qualified name"