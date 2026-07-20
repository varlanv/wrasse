package sample

import sample.SamePackageThing

// fixture-aux-file: aux/SamePackageThing.kt

val x = SamePackageThing()

// expect-clean
