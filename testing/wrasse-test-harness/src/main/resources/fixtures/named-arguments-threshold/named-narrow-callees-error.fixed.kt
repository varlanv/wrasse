package sample

class Row {
    fun long(index: Int): Long = index.toLong()

    fun string(index: Int): String = index.toString()
}

object Inst {
    fun fromMillis(millis: Long): Long = millis
}

fun items(vararg xs: Int): Int = xs.size

fun demo(row: Row): Long {
    val stamp = Inst.fromMillis(row.long(5) / 1000)
    val flag = row.string(6) == "True"
    val kept = items(xs = intArrayOf(1, 2))
    return stamp + flag.hashCode() + kept
}