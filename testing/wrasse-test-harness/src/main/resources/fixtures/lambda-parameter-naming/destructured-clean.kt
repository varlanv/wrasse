package sample

fun run() {
    mapOf(1 to 2).forEach { (key, value) -> println(key to value) }
}

// expect-clean
