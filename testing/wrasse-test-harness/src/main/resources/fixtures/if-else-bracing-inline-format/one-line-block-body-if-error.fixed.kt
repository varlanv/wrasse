package sample

class Inventory(private val items: List<String>) {
    fun describe(): String {
        if (items.isEmpty()) {
            return "empty"
        } else {
            return "${items.size} items"
        }
    }
}