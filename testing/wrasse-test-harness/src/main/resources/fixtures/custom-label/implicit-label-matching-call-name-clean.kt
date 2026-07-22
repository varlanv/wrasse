package sample

fun foo(): Int = runCatching {
    return@runCatching 1
}.getOrDefault(0)

// expect-clean
