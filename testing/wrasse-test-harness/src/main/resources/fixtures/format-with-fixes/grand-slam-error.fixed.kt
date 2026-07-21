package sample

import kotlin.math.PI
import kotlin.math.abs

class Box {
    val x = 1

    fun show(): Double {
        if (x > 0) {
            return abs(-1.0) + PI
        } else {
            return 0.0
        }
    }
}