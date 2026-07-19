package sample

import kotlin.text.Regex
import kotlin.math.PI

val x = PI
val y = Regex("a")

// expect-error 3:1 import-ordering "Imports are not sorted"
