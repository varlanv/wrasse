package sample

fun f(a: String, b: String) {}

fun demo() {
    f("first argument here", "second argument here")
}

// expect-error 1:1 format "File is not wrasse-formatted"
