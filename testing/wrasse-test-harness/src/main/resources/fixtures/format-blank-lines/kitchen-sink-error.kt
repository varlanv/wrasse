package sample
import kotlin.math.PI

class Box

constructor(val value: Int) {

    fun show(): Int {
        val a = value


        val b = a + 1

        return b

    }

}

fun classify(x: Int): String = when (x) {
    1 -> "one"


    else -> "other"
}

fun area(radius: Double): Double = PI * radius * radius

// expect-error 1:1 format "File is not wrasse-formatted"
