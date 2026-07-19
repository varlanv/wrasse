package sample.auxop

import sample.auxbase.Vec

operator fun Vec.plus(other: Vec): Vec = Vec(x + other.x, y + other.y)
