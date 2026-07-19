package sample

import sample.aux.`weird fun`

// fixture-aux-file: aux/Aux.kt

fun sample(): Int = `weird fun`()

// expect-clean
