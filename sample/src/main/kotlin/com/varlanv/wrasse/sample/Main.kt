package com.varlanv.wrasse.sample

// A greeting function
fun greet(name: String): String {
    return "Hello, $name!"
}

/**
 * Entry point
 */
fun main() {
    println(greet("Wrasse"))
    println(add(2, 3))
}

/* inline comment */ fun add(a: Int, b: Int): Int {
    return a + b
}
