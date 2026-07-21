package sample

class Registry {
    operator fun get(block: () -> Int): Int = block()
}

fun demo(): Int {
    val registry = Registry()
    return registry[{ 123 }]
}