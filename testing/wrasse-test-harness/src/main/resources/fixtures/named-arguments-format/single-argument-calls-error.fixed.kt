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
    id = row.long(column = "trade_id"),
    at = Inst.fromMillis(millis = row.long(column = "timestamp")),
    price = row.decimal(column = "price"),
    side = if (row.pick(column = "side", index = 1) == "buy") {
        "bid"
    } else {
        "ask"
    },
    volume = row.decimal(column = "volume"),
)