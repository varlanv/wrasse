package sample

fun main() {
    val r = call(
        1,
        // wrasse fmt:off
        2,    3,
           4,
        // wrasse fmt:on
        5,
    )
}

fun call(
    a: Int,
    b: Int,
    c: Int,
    d: Int,
    e: Int,
) = a