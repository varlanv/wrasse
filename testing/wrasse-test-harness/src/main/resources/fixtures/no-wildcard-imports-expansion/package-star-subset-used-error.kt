package sample

import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()
val n: Outer.Nested? = null
val total = auxTopLevelFun()
val s = Status.ACTIVE

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
