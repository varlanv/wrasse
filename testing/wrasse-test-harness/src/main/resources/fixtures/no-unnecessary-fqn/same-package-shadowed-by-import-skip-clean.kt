package sample.aux

import sample.shadow.Widget
// fixture-aux-file: aux/AuxSamePackageSibling.kt
// fixture-aux-file: aux/AuxShadow.kt

val w: sample.aux.Widget = TODO()
val other: Widget = TODO()

// expect-clean
