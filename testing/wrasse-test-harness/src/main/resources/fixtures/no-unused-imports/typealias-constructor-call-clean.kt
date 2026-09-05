package sample

import sample.aux.Decimal
import sample.aux.Plain

// fixture-aux-file: aux/TypeAliases.kt

fun pair(): List<Any> = listOf(Decimal("1"), Plain("2"))

// expect-clean
