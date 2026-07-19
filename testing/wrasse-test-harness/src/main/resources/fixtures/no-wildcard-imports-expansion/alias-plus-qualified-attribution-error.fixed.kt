package sample

import sample.aux.Outer as O
import sample.aux.Gadget
import sample.aux.Outer

val aliased = O()
val qualified: sample.aux.Outer.Nested? = null
val g = Gadget()