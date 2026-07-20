package sample

import kotlin.math.abs; import kotlin.text.Regex
import kotlin.math.max

fun sample(): Int = abs(-1)

// expect-error 3:25 no-unused-imports "Unused import (no autofix for this shape)"
// expect-error 4:1 no-unused-imports "Unused import"
