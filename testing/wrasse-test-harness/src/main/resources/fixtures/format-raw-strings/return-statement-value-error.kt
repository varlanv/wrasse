package sample

fun poem(): String {
    return """
    Roses are red
    Violets are blue
    """.trimIndent()
}

// expect-error 1:1 format "File is not wrasse-formatted"
