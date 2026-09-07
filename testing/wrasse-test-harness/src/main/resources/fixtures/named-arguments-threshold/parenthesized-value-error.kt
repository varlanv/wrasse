package sample

fun parenSimple(and: Boolean): Boolean = and
fun parenBinary(delta: Int): Int = delta
fun withLambda(block: () -> Int): Int = block()
fun withString(text: String): String = text
fun withAnnotated(value: Int): Int = value
fun withLabeled(value: Int): Int = value

fun demo(x: Boolean, y: Int): Int {
    val a = parenSimple(and = (x))
    val b = parenBinary(delta = (y) + 1)
    val c = withLambda(block = { 1 })
    val d = withString(text = "hello")
    val e = withAnnotated(value = @Suppress("UNUSED_EXPRESSION") y)
    val f = withLabeled(value = label@ y)
    return b + c + d.length + e + f + if (a) 1 else 0
}

// expect-error 11:24 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 12:24 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 13:23 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 14:23 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 15:26 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 16:24 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
