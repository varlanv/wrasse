package sample

fun demo() {
    val x = """
        line one
        line two
    """.trimIndent()
}

// expect-error 1:1 format "File is not wrasse-formatted"
