package sample

import kotlin.time.Duration.Companion.seconds

class WeightRateLimitConfig(
    val header: String,
    val limit: Int,
    val shared: Boolean,
    val window: kotlin.time.Duration,
    val retries: Int,
    val backoff: kotlin.time.Duration,
)

fun startApp() {
    val binanceWeightHeader = "x-mbx-used-weight-1m"
    val binanceFuturesConfig = WeightRateLimitConfig(binanceWeightHeader, 1600, false, 10.seconds, 2, 5.seconds)
    val binanceSpotConfig = WeightRateLimitConfig(binanceWeightHeader, 1600, false, 10.seconds, 2, 5.seconds)
    println(binanceFuturesConfig)
    println(binanceSpotConfig)
}