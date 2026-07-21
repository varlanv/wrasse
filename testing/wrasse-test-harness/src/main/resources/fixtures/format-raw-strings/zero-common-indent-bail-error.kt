package sample

fun poem(): String {
    val text = """
line one
line two
    """.trimIndent()
    return text
}

// expect-error 1:1 format "File is not wrasse-formatted"
