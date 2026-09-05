package sample

fun describe(count: Int): String = "count=$count"

fun report(flag: Boolean, count: Int): String {
    val label = if (flag) {
        describe(count)
    } else {
        "none"
    }
    return describe(
        if (count > 0) {
            count
        } else {
            0
        },
    ) + label
}