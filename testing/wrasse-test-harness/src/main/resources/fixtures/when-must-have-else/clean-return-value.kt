package sample

fun describe(flag: Boolean): String {
    return when (flag) {
        true -> "yes"
        false -> "no"
    }
}

// expect-clean
