package sample

fun g(a: String, b: String) = a + b

fun f(x: String) = x

fun demo() {
    f(
        g(
            "first argument here",
            "second argument here",
        ),
    )
}