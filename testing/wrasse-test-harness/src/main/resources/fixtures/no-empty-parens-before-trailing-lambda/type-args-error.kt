package sample

fun <T> make(f: () -> Unit) {}

fun main() {
    make<Int>() { }
}

// expect-error 6:14 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
