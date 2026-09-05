package sample

class Inventory(private val items: List<String>) {
    fun describe(): String { if (items.isEmpty()) return "empty" else return "${items.size} items" }
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 4:51 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 4:71 if-else-bracing "Missing braces on branch of if-statement"
