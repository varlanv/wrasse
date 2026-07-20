package sample

import sample.SamePackageThing

// fixture-aux-file: aux/SamePackageThing.kt

val x = 1

// expect-error 3:1 no-unused-imports "Unused import"
