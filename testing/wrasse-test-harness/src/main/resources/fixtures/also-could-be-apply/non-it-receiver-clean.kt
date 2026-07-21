package sample

fun make(a: Int): Int = a.also { println(it) }

// expect-clean
