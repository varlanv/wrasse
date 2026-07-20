package sample

class Box(val value: Int) {
    val doubled: Int = value * 2

    fun describe(): String {
        return "value=$value"
    }
}