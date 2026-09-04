package sample

private class Trade(
    val at: Long,
    val price: String,
    val qty: String = "1",
    val id: Long = 0,
)

fun demo() {
    val t = Trade(at = 1L, price = "100", qty = "2", id = 3L)
    println(t)
}