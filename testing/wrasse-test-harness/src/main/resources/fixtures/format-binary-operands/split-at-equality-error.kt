package sample

class Entry(val type: String, val next: Entry?)

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
        return indexPrice ==
        null &&
            markPrice ==
            null &&
            fundingRate ==
            null &&
            openInterest ==
            null &&
            topLongShortPositionRatio ==
            null &&
            longShortAccountRatio ==
            null &&
            bookTicker ==
            null &&
            priceChange24h ==
            null
    }
}

fun nextIsSuperTypeList(entries: List<Entry>, suppress: Boolean): Boolean {
    val nextIsSuperTypeList = suppress &&
        entries.getOrNull(1)?.type ==
        "colon" &&
        entries.getOrNull(2)?.type ==
        "supertypes"
    return nextIsSuperTypeList
}

fun isEffectivelyEmpty(entry: Entry?): Boolean =
    entry ==
    null ||
        entry.type ==
        "rbrace" ||
        entry.type ==
        "lbrace" ||
        (entry.next != null && entry.next.type.isEmpty())

fun spacing(prevType: String, nextType: String): String {
    if ((nextType == "parameters" || nextType == "arguments") &&
        prevType !=
        "function-type" &&
        prevType !=
        "function-literal"
    ) {
        return ""
    }
    return prevType
}

// expect-error 1:1 format "File is not wrasse-formatted"
