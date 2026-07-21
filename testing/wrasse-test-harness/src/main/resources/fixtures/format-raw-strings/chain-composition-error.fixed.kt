package sample

fun shout(): String {
    val text = """
        hello
        world
        """
        .trimIndent()
        .uppercase()
    return text
}