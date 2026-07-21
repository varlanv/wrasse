package sample

annotation class A(val v: String)

fun c(): Int {
    val f = @A("x") { y: Int -> y }
    return f(1)
}

// expect-clean
