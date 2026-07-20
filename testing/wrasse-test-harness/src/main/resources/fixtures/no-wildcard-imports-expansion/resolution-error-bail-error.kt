package sample

import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()
val bad = unresolvedThing()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
