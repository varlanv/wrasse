package sample

sealed class Shape {
    class Circle(val radius: Int) : Shape()

    class Square(val side: Int) : Shape()
}

interface Task {
    fun run()
}

class Describer(val shape: Shape) {
    fun describe(): String = when (shape) {
        is Shape.Circle -> "circle"
        is Shape.Square -> "square"
    }

    fun task(): Task = object : Task {
        override fun run() {
        }
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 14:28 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 19:22 forbidden-expression-body-functions "Function body must be a block, not an expression"
