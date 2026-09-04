package sample

class Dec64(val value: Long)

class AnalyticsMarketData {
    class Aggregated {
        class Bingx {
            class Futures {
                class FuturesCandlestick1Min(
                    val open: Dec64,
                    val close: Dec64,
                    val high: Dec64,
                    val low: Dec64,
                    val volume: Long,
                    val time: Long,
                )
            }
        }
    }
}

fun writeItem(item: AnalyticsMarketData.Aggregated.Bingx.Futures.FuturesCandlestick1Min) {
    println(item)
}

fun processItem(
    open: Dec64,
    close: Dec64,
    high: Dec64,
    low: Dec64,
    volume: Long,
    time: Long,
) {
    writeItem(
        AnalyticsMarketData
            .Aggregated
            .Bingx
            .Futures
            .FuturesCandlestick1Min(
                open = open,
                close = close,
                high = high,
                low = low,
                volume = volume,
                time = time,
            ),
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
