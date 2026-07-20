package sample.aux2

class Coord(val x: Int, val y: Int, val z: Int)

operator fun Coord.component1(): Int = x

operator fun Coord.component2(): Int = y

operator fun Coord.component3(): Int = z
