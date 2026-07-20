package sample

fun fooBar1() {
    foo("some-foo");
    {
        // Do something
    }.bar()
}

fun fooBar2() {
    foo("some-foo"); { /* Do something */ }.bar()
}

fun foo(input: String, block: ((String) -> Unit)? = null) {
    if (block != null) {
        block(input)
    } else {
        input
    }
}

fun <R> (() -> R).bar() {
    // Do something
}

// expect-clean
