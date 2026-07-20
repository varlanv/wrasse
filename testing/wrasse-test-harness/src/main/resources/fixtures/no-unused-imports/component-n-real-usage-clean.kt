package sample

import sample.aux2.Coord
import sample.aux2.component1
import sample.aux2.component2
import sample.aux2.component3

// fixture-aux-file: aux/ComponentExt.kt

fun use() {
    val (a, b, c) = Coord(1, 2, 3)
    println("$a$b$c")
}

// expect-clean
