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
    val stamp = Inst.fromMillis(millis = row.long(index = 5) / 1000)
    val flag = row.string(index = 6) == "True"
    val kept = items(xs = intArrayOf(1, 2))
    return stamp + flag.hashCode() + kept
}

// expect-error 16:32 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 16:50 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 17:26 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
