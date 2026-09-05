package sample

fun pick(flag: Boolean, a: String): Pair<String, String> = Pair(
    first = if (flag) {
        a
    } else {
        a + a
    },
    second = a,
)

// expect-clean
