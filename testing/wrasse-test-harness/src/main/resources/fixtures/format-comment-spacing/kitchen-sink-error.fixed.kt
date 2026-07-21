package sample

// needs a space after the slashes
class Widget(val name: String) {
    fun label(): String {
        return name // trailing comment touching code
    }
}