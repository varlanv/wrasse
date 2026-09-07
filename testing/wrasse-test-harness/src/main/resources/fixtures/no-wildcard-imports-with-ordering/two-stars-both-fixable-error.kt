package sample

import sample.aux.*
import sample.extra.*

// fixture-aux-file: aux/Aliases.kt
// fixture-aux-file: aux/Real.kt
// fixture-aux-file: aux/Extra.kt

fun demo(): Pair<Other, Widget> = Pair(Other(), Widget())

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 4:1 no-wildcard-imports "Replace wildcard import with explicit imports"
