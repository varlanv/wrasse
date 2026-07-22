package sample

fun describe(flag: Boolean): String = when (flag) {
    true -> "yes"
    false -> "no"
}

// expect-clean
