package sample

fun sum(vararg numbers: Int): Int = numbers.sum()

fun demo(values: IntArray): Int {
    return sum(*values)
}