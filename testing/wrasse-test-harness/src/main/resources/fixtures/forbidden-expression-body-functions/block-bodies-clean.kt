package sample

class Box(val value: Int) {
    fun block(): Int {
        return value
    }

    val doubled: Int
        get() = value * 2
}

// expect-clean
