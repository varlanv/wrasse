package sample

import sample.aux.StringPair

// fixture-aux-file: aux/Aux.kt

fun sample(): StringPair = Pair("a", "b")

// expect-clean
