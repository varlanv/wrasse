package sample

import sample.auxflip.*
// fixture-aux-file: aux/AuxFlip.kt

val bareList: List<Int> = listOf(1, 2, 3)
val qualified = sample.auxflip.List()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
