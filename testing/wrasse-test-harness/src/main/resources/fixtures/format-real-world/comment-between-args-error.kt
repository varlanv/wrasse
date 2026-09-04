package sample

fun listOf(vararg items: String): List<String> = items.toList()

fun buildClients(): List<String> {
    return listOf(
        "binance-spot",
            // Separate rate bucket for futures data endpoints.
            // Empirically the cap is loosely enforced.
        "binance-futures-open-interest-hist",
        "binance-futures-ls-position-ratio",
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
