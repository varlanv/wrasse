package sample

import sample.aux.*
import sample.aux2.*
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/Aux2.kt

/**
 * See [Sensor] for details.
 */
val w = Widget()
val s = Sensor()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
// expect-error 4:1 no-wildcard-imports "Replace wildcard import with explicit imports"
