package sample

class Box {
    val x = 1

    fun show(): Int {
        return x
    }

    fun other(): Int {
        return x + 1
    }

    init {
        println(x)
    }
}