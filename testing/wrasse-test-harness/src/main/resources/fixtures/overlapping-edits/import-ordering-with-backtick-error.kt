package sample

import repro.`internal`.Thing
import repro.Other
// fixture-aux-file: aux/Thing.kt
// fixture-aux-file: aux/Other.kt

fun use(): Thing = Thing()

fun useOther(): Other = Other()

// expect-error 3:1 import-ordering "Imports are not sorted"
// expect-error 1:1 format "File is not wrasse-formatted"
