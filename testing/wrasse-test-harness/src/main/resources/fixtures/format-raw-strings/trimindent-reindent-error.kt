package sample

fun poem(): String {
    val text = """
    Roses are red
    Violets are blue
    """.trimIndent()
    return text
}

// expect-error 1:1 format "File is not wrasse-formatted"
