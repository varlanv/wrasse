package sample

fun f(a: String, b: String) {}

fun demo() {
    f("first argument is long", "second argument is long")
}

// expect-error 1:1 format "File is not wrasse-formatted"
