package sample

fun foo(f: () -> Unit) {}
fun bar(f: () -> Unit) {}

fun main() {
    foo() { bar() { } }
}

// expect-error 7:8 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
// expect-error 7:16 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
