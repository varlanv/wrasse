package sample

import kotlin.math.abs; import kotlin.text.Regex
import kotlin.math.max

fun sample(): Int = abs(-1)

// expect-error 3:25 no-unused-imports "Unused import"
// expect-error 4:1 no-unused-imports "Unused import"
