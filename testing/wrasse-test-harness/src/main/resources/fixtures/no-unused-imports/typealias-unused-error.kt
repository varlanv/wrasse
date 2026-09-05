package sample

import sample.aux.Big
import sample.aux.Decimal

// fixture-aux-file: aux/TypeAliases.kt

fun big(): Big = Big("1")

// expect-error 4:1 no-unused-imports "Unused import"
