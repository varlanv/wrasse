package sample

fun pick(a: Boolean, b: Boolean): String {
    val side = if (a) {
        "bid"
    } else if (b) { "ask" } else { "none" }
    return side
}

// expect-error 1:1 format "File is not wrasse-formatted"
