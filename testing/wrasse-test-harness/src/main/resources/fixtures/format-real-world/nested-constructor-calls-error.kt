package sample

import kotlin.time.Duration.Companion.seconds

class TimeRateLimitedConfig(
    val window: kotlin.time.Duration,
    val limit: Int,
    val burst: Int,
    val backoff: kotlin.time.Duration,
    val retries: Int,
)

class TimeRateLimitedClient(val config: TimeRateLimitedConfig)

class PredicatedClient(
    val client: TimeRateLimitedClient,
    val predicate: (String) -> Boolean,
    val label: String,
)

fun buildClients(): List<PredicatedClient> {
    return listOf(
        PredicatedClient(
            TimeRateLimitedClient(
                TimeRateLimitedConfig(5.seconds, 1000, 5, 5.seconds, 2),
            ),
            predicate = { it.contains("futures/data/openInterestHist") },
            label = "binance-futures-open-interest-hist",
        ),
        PredicatedClient(
            TimeRateLimitedClient(
                TimeRateLimitedConfig(5.seconds, 1000, 5, 5.seconds, 2),
            ),
            predicate = { it.contains("futures/data/topLongShortPositionRatio") },
            label = "binance-futures-ls-position-ratio",
        ),
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
