package sample

fun pick(flag: Boolean): String {
    val side = if (flag) { "bid" } else { "ask" }
    return side
}

// expect-clean
