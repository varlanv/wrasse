package sample

class Box(val x: Int) {
    constructor() : this(0)
    fun show(): Int = x
}

// expect-error 1:1 format "File is not wrasse-formatted"
