package sample

import sample.auxobj.Config.*
// fixture-aux-file: aux/AuxObject.kt

val v = value

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
