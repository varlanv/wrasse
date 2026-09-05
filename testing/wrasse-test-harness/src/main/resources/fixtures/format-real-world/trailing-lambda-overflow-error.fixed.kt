package sample

fun aggregateCandles(
    items: List<Long>,
    intervalMillis: Long,
    combine: (Long, Long, Long, Long, Long, Long, Long) -> Long,
): List<Long> = items.map { combine(it, it, it, it, it, it, intervalMillis) }

fun derive(collectedTradeItems: List<Long>, initialAccumulatorValue: Long): List<Long> {
    val folded = collectedTradeItems.fold(
        initialAccumulatorValue,
    ) { accumulator, tradeItem -> accumulator + tradeItem * 2 + 1 }
    println(folded)
    return aggregateCandles(
        collectedTradeItems,
        intervalMillis = 60_000L,
    ) { bucketStart, open, high, low, close, volume, amount ->
        bucketStart + open + high + low + close + volume + amount
    }
}