package sample

object A {
    object B {
        fun greet(f: () -> Unit) {}
    }
}

fun main() {
    A.B.greet() { }
}

// expect-error 10:14 no-empty-parens-before-trailing-lambda "Unnecessary empty parentheses before trailing lambda"
