package sample

enum class Color { RED, GREEN, BLUE }

fun foo(c: Color) {
    when (c) {
        RED -> println("red")
        GREEN -> println("green")
        BLUE -> println("blue")
    }
}

// expect-clean
