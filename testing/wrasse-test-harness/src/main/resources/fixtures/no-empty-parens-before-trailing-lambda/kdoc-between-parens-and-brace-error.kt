package sample

fun greet(f: () -> Unit) {}

fun main() {
    greet() /** kdoc */ { }
}

// expect-error 6:10 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
