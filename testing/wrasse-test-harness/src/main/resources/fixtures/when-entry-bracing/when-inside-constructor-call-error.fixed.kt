package sample

class Box(val label: String)

fun make(x: Int): Box = Box(
    when (x) {
        1 -> {
            "one"
        }
        2 -> {
            "two"
        }
        else -> {
            "other"
        }
    }
)