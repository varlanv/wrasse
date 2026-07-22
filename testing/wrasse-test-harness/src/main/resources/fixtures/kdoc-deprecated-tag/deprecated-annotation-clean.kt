package sample

/**
 * This function prints a message.
 */
@Deprecated("Useless, replace with println.")
fun printThenNewline(what: String) {
    println(what)
}

// expect-clean
