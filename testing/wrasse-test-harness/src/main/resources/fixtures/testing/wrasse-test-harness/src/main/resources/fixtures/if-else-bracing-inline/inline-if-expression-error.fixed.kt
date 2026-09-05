package sample

fun pick(flag: Boolean): String {
    val a = if (flag) {
        "1"
    } else {
        "2"
    }
    return a
}