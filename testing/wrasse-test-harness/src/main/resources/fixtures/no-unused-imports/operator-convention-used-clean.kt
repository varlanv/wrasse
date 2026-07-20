package sample

import sample.auxop2.Box
import sample.auxop2.Point
import sample.auxop2.combine
import sample.auxop2.plusAssign
import sample.auxop2.rangeTo
import sample.auxop2.rangeUntil

// fixture-aux-file: aux/Operators.kt

fun use() {
    val b1 = Box(1)
    val b2 = Box(2)
    val combined = b1 combine b2
    b1 += 3
    val p1 = Point(1)
    val p2 = Point(2)
    val r1 = p1..p2
    val r2 = p1..<p2
    println("$combined $r1 $r2")
}

// expect-clean
