package sample

class Box(val value: Int) {
    fun mapped(items: List<Int>): List<Int> {
        return items.map {
            it + value
        }
    }

    fun single(): Int {
        return value + 1
    }
}