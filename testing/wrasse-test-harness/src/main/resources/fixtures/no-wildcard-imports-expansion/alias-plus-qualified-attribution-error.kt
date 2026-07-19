package sample

import sample.aux.Outer as O
import sample.aux.*
// fixture-aux-file: aux/Aux.kt

val aliased = O()
val qualified: sample.aux.Outer.Nested? = null
val g = Gadget()

// expect-error 4:1 no-wildcard-imports "Replace wildcard import with explicit imports"
