package sample

open class Open

fun f() {
    object : Open() {}
}

// expect-clean
