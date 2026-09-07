package sample

import sample.aux.*

// fixture-aux-file: aux/Aliases.kt
// fixture-aux-file: aux/Real.kt

fun demo(): Pair<Boom, Other> = Pair(Boom(), Other())

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
