package sample

fun greet(f: () -> Unit) {}

fun main() {
    greet() /* trailing */ { }
}

// expect-error 6:10 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
