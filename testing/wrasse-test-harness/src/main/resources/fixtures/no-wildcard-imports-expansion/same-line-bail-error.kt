package sample

import sample.aux.Widget; import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val w = Widget()

// expect-error 3:27 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
