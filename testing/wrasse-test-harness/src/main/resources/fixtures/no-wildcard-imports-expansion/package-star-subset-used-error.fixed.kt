package sample

import sample.aux.Outer
import sample.aux.Status
import sample.aux.Widget
import sample.aux.auxTopLevelFun

val w = Widget()
val n: Outer.Nested? = null
val total = auxTopLevelFun()
val s = Status.ACTIVE