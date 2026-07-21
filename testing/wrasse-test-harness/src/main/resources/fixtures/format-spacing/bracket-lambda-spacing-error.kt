package sample

class Registry {
    operator fun get(block: () -> Int): Int = block()
}

fun demo(): Int {
    val registry = Registry()
    return registry[ { 123 } ]
}

// expect-error 1:1 format "File is not wrasse-formatted"
