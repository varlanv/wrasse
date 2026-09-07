package sample

class Snapshot {
    var indexPrice: Int? = null
    var markPrice: Int? = null
    var fundingRate: Int? = null
    var openInterest: Int? = null
    var topLongShortPositionRatio: Int? = null
    var longShortAccountRatio: Int? = null
    var bookTicker: Int? = null
    var priceChange24h: Int? = null

    fun isNotTouched(): Boolean {
        return indexPrice == null &&
            markPrice == null &&
            fundingRate == null &&
            openInterest == null &&
            topLongShortPositionRatio == null &&
            longShortAccountRatio == null &&
            bookTicker == null &&
            priceChange24h == null
    }
}