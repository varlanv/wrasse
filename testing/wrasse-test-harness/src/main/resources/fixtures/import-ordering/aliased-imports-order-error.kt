package sample

import kotlin.text.Regex as Rx2
import kotlin.text.Regex as Rx1

val a = Rx1("x")
val b = Rx2("y")

// expect-error 3:1 import-ordering "Imports are not sorted"
