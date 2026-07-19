package sample

import sample.aux.Obj.member

// fixture-aux-file: aux/Aux.kt

val x = 1

// expect-error 3:1 no-unused-imports "Unused import"
