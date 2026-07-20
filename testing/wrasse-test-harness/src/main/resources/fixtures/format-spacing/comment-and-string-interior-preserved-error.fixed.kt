package sample

/**
 * Demonstrates that spacing normalization never touches comment or string interiors,
 * even oddly-spaced   code   like    this.
 */
class Notes {
    // a  comment   with   odd   spacing,stays,exactly,as-is
    val template = "a  +  b , c :Int"

    fun describe(): String {
        return "value=$template"
    }
}