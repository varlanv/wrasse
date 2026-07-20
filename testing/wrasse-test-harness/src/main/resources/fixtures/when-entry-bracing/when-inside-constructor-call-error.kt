package sample

class Box(val label: String)

fun make(x: Int): Box = Box(
    when (x) {
        1 -> {
            "one"
        }
        2 -> "two"
        else -> "other"
    }
)

// expect-error 10:14 when-entry-bracing "Missing braces on when-entry body"
// expect-error 11:17 when-entry-bracing "Missing braces on when-entry body"