package sample

class Docs {
    /**
 * Greets someone.
   * Oddly aligned continuation line, on purpose.
 */
    fun greet(): String {
        val banner = """
Hello
    World
"""
        return banner
    }
}