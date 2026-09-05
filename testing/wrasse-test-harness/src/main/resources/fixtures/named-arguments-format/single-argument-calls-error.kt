package sample

class Row {
    fun long(column: String): Long = column.length.toLong()

    fun decimal(column: String): Double = column.length.toDouble()

    fun pick(column: String, index: Int): String = column + index
}

class Trade(
    val id: Long,
    val at: Long,
    val price: Double,
    val side: String,
    val volume: Double,
)

object Inst {
    fun fromMillis(millis: Long): Long = millis
}

fun read(row: Row): Trade = Trade(
    row.long("trade_id"),
    Inst.fromMillis(row.long("timestamp")),
    row.decimal("price"),
    if (row.pick(column = "side", index = 1) == "buy") {
        "bid"
    } else {
        "ask"
    },
    row.decimal("volume"),
)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 23:34 named-arguments "Positional arguments should be named"
// expect-error 24:13 named-arguments "Positional arguments should be named"
// expect-error 25:20 named-arguments "Positional arguments should be named"
// expect-error 25:29 named-arguments "Positional arguments should be named"
// expect-error 26:16 named-arguments "Positional arguments should be named"
// expect-error 32:16 named-arguments "Positional arguments should be named"
