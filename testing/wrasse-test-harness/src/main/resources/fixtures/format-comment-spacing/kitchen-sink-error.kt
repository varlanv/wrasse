package sample

//needs a space after the slashes
class Widget(val name: String) {
    fun label(): String {
        return name//trailing comment touching code
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
