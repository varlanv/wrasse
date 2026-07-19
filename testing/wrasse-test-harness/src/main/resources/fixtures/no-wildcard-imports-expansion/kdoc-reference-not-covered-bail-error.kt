package sample

import sample.aux.*
// fixture-aux-file: aux/Aux.kt

/**
 * See [Gadget] for details.
 */
val w = Widget()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
