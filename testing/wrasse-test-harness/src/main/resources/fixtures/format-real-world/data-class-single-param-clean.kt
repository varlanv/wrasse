package sample

data class BinanceMonthlyDate(val date: String) {
    val formatted: String

    init {
        formatted = date.uppercase()
    }
}

// expect-clean
