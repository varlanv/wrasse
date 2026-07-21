package sample

fun poem(): String {
    val text = """
Some text starting at the beginning of the line
    Some text not starting at the beginning of the line
    """
        .trimIndent()
    return text
}

// expect-clean
