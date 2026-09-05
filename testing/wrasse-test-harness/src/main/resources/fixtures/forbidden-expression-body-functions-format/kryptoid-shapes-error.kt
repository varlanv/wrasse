package sample

class Window(val startMillis: Long, val endMillis: Long)

class CompetitionContext(private val byAsset: Map<String, List<Window>>) {
    fun imminentCount(asset: String, atMillis: Long, withinDays: Long = 7L): Int = byAsset[asset]?.count {
        atMillis <
            it.startMillis &&
            it.startMillis <=
            atMillis +
            withinDays *
            DAY_MILLIS
    } ?: 0

    private fun researches(): Sequence<String> = sequenceOf(
        "smoke",
        "kline",
    )

    private companion object {
        const val DAY_MILLIS = 86400000L
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:82 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 15:48 forbidden-expression-body-functions "Function body must be a block, not an expression"
