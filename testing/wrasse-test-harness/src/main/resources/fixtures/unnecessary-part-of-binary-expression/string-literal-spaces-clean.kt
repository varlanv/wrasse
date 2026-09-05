package sample

fun check(lower: String): Boolean {
    val boughtBack = "buyback" in lower || "buy back" in lower
    return boughtBack || lower.endsWith("for spot") || lower.endsWith("for spot ")
}

// expect-clean
