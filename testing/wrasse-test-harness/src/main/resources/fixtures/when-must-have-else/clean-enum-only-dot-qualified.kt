package sample

enum class Color { RED, GREEN, BLUE }

fun foo(c: Color) {
    when (c) {
        Color.RED -> println("red")
        Color.GREEN -> println("green")
        Color.BLUE -> println("blue")
    }
}

// expect-clean
