package sample.clock

fun now(): Long = 0L

class Ticker(val value: Int) {
    fun tick(): Int = value

    fun tickBy(n: Int): Int = value + n
}
