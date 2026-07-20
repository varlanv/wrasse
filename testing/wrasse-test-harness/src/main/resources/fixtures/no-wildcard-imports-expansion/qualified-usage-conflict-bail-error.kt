package sample

import sample.auxc2.Item
import sample.auxc1.*
// fixture-aux-file: aux/AuxC1.kt
// fixture-aux-file: aux/AuxC2.kt

val q = Item()
val p = sample.auxc1.Item.make()

// expect-error 4:1 no-wildcard-imports "Replace wildcard import with explicit imports (no autofix for this shape)"
