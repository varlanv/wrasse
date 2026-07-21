package sample

fun openingTouch(): String {
    val text = """Roses are red
        Violets are blue
        """
        .trimIndent()
    return text
}

fun closingTouch(): String {
    val text = """
        Roses are red
        Violets are blue"""
        .trimIndent()
    return text
}

// needs a space
fun marker(): Int = 1