package sample.auxop2

class Box(var value: Int)

infix fun Box.combine(other: Box): Int = value + other.value

operator fun Box.plusAssign(delta: Int) {
    value += delta
}

class Point(val x: Int)

operator fun Point.rangeTo(other: Point): IntRange = x..other.x

operator fun Point.rangeUntil(other: Point): IntRange = x until other.x
