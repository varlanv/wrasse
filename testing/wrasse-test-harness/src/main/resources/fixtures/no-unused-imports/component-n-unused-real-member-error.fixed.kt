package sample

import sample.aux2.Coord
import sample.aux2.component1
import sample.aux2.component2


fun use() {
    val (a, b) = Coord(1, 2, 3)
    println("$a$b")
}