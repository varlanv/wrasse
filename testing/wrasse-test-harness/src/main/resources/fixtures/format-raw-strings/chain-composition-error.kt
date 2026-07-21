package sample

fun shout(): String {
    val text = """
    hello
    world
    """.trimIndent().uppercase()
    return text
}

// expect-error 1:1 format "File is not wrasse-formatted"
