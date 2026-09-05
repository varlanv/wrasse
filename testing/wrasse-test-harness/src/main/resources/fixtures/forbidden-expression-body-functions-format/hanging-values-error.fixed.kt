package sample

sealed class Shape {
    class Circle(val radius: Int) : Shape()

    class Square(val side: Int) : Shape()
}

interface Task {
    fun run()
}

class Describer(val shape: Shape) {
    fun describe(): String {
        return when (shape) {
            is Shape.Circle -> "circle"
            is Shape.Square -> "square"
        }
    }

    fun task(): Task {
        return object : Task {
            override fun run() {
            }
        }
    }
}