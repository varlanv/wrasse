package sample

fun greet(name: String): String {
    val greeting = "Hello, $name"   // already-wide gap before this comment, left alone
    return greeting
}

// expect-clean
