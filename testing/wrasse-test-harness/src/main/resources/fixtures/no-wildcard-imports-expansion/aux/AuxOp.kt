package sample.auxop

class Vec(val x: Int, val y: Int)

operator fun Vec.plus(other: Vec): Vec = Vec(1, 1)
operator fun Vec.component1(): Int = x
operator fun Vec.component2(): Int = y
