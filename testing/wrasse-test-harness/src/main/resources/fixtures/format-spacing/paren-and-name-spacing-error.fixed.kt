package sample

fun demo(a: Int) {
    if (a > 0) {
        println(a)
    }
    demo(a - 1)
}