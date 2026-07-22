package sample

/**
 * This function prints a message.
 *
 * @deprecated Useless, replace with println.
 */
fun printThenNewline(what: String) {
    println(what)
}

// expect-error 3:1 kdoc-deprecated-tag "The @deprecated tag block does not properly report deprecation in Kotlin, use @Deprecated annotation instead"
