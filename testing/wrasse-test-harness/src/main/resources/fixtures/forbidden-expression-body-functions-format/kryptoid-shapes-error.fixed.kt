package sample

class Window(val startMillis: Long, val endMillis: Long)

class CompetitionContext(private val byAsset: Map<String, List<Window>>) {
    fun imminentCount(
        asset: String,
        atMillis: Long,
        withinDays: Long = 7L,
    ): Int {
        return byAsset[asset]?.count {
            atMillis < it.startMillis && it.startMillis <= atMillis + withinDays * DAY_MILLIS
        } ?: 0
    }

    private fun researches(): Sequence<String> {
        return sequenceOf("smoke", "kline")
    }

    private companion object {
        const val DAY_MILLIS = 86400000L
    }
}