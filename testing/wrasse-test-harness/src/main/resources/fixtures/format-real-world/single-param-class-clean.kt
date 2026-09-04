package sample

class KlineSeries(val bars: List<String>) {
    fun first(): String = bars.first()
}

// expect-clean
