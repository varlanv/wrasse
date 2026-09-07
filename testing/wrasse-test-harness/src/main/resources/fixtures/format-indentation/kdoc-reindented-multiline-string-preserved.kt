package sample

class Docs {
/**
 * Greets someone.
   * Oddly aligned continuation line.
 */
fun greet(): String {
val banner = """
Hello
    World
"""
return banner
}
}

// expect-error 1:1 format "File is not wrasse-formatted"
