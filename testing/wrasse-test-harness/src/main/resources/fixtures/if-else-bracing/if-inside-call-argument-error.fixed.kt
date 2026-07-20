package sample

fun foo(x: Int, y: Int, z: Int) {}

fun demo(a: Int, b: Int, c: Int, d: Int, bar: Boolean) {
    foo(
        a,
        if (bar) {
            b
        } else {
            c
        },
        d
    )
}