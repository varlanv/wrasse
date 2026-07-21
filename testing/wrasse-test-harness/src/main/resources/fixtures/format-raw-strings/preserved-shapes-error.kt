package sample

fun withInterpolation(name: String): String {
    val greeting = """
  hello $name
  """.trimIndent()
    return greeting
}

fun withMargin(): String {
    val text = """
    |line one
    |line two
    """.trimMargin()
    return text
}

fun withoutTrim(): String {
    val text = """
    raw content
    """
    return text
}

//needs a space
fun marker(): Int = 1

// expect-error 1:1 format "File is not wrasse-formatted"
