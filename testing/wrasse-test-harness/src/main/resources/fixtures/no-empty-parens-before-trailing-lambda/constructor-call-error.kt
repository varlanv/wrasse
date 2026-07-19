package sample

class Box(val f: () -> Unit)

fun main() {
    Box() { }
}

// expect-error 6:8 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
