package sample

fun openTouch(): String {
    val a = """content on first line
    more content
    """.trimIndent()
    return a
}

fun closeTouch(): String {
    val b = """
    more content
    content on last line""".trimIndent()
    return b
}

// expect-error 1:1 format "File is not wrasse-formatted"
