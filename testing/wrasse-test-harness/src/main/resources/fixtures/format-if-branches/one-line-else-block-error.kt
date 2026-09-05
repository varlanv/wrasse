package sample

fun pick(flag: Boolean): String {
    val side = if (flag) {
        "bid"
    } else { "ask" }
    return side
}

// expect-error 1:1 format "File is not wrasse-formatted"
