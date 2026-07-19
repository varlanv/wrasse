package sample

import sample.aux2.Other as Widget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/AuxOther.kt

val o: Widget = TODO()
val w: sample.aux.Widget = TODO()

// expect-clean
