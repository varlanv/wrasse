package sample

class Desk(private val adaptersByExchange: Map<String, String>) {
    fun exchangesWithAdapter(request: List<String>): List<String> {
        val exchangesWithAdapter = request.filter { exchange -> adaptersByExchange.containsKey(exchange) && exchange.isNotEmpty() }
        return exchangesWithAdapter
    }

    fun aggregate(marketDataSourceWithLongName: List<String>): List<String> {
        val aggregatedCandlesticksByMinuteBucket = marketDataSourceWithLongName.mapCollectedIntoCandles { collectedItems ->
            println(collectedItems)
        }
        return aggregatedCandlesticksByMinuteBucket
    }

    fun chain(items: List<String>): String {
        val joined =
            items.map { it.trim() }.filter { it.isNotEmpty() }.map { it.uppercase() }.sortedDescending().joinToString(separator = ", ")
        return joined
    }
}

fun List<String>.mapCollectedIntoCandles(block: (List<String>) -> Unit): List<String> {
    block(this)
    return this
}

// expect-error 1:1 format "File is not wrasse-formatted"
