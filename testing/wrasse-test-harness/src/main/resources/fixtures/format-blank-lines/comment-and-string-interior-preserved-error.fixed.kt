package sample

/**
 * Documentation with an intentional blank line below.
 *
 *
 * Both of those blank comment lines above must stay exactly as written.
 */
class Notes {
    val template = """
        first line

        second line after a blank line preserved verbatim
    """

    fun describe(): String {
        return template
    }
}