package sample

fun poem(): String {
    val text = """
        Roses are red
        Violets are blue
        """
        .trimIndent()
    return text
}

// expect-clean
