package sample

fun isMonth(w: String): Boolean {
    return when (w) {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
        "Aug", "Sep", "Oct", "Nov", "Dec", "January",
        "February", "March", "April", "June", "July",
        "August", "September", "October", "November",
        "December" -> true
        else -> false
    }
}