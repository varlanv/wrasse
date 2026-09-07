package sample

fun parenSimple(and: Boolean): Boolean = and
fun parenBinary(delta: Int): Int = delta
fun withLambda(block: () -> Int): Int = block()
fun withString(text: String): String = text
fun withAnnotated(value: Int): Int = value
fun withLabeled(value: Int): Int = value

fun demo(x: Boolean, y: Int): Int {
    val a = parenSimple((x))
    val b = parenBinary((y) + 1)
    val c = withLambda({ 1 })
    val d = withString("hello")
    val e = withAnnotated(@Suppress("UNUSED_EXPRESSION") y)
    val f = withLabeled(label@ y)
    return b + c + d.length + e + f + if (a) 1 else 0
}