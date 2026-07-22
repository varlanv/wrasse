package sample

fun foo(): List<String> {
    return listOf("a").map {
        return@map it
    }
}

// expect-clean
