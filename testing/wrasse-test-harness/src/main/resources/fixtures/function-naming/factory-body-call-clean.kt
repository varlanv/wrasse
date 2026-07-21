package sample

class Baz(val value: Int)

fun Baz(text: String) = Baz(text.length)

// expect-clean
