package sample

import sample.aux.*
import sample.aux.sub.SubWidget
// fixture-aux-file: aux/Aux.kt
// fixture-aux-file: aux/sub/SubWidget.kt

val w = Widget()
val s = SubWidget()

// expect-error 3:1 no-wildcard-imports "Replace wildcard import with explicit imports"
