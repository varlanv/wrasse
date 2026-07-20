package sample

fun demo() {
    if (true) {
        doSomething()
    } else
        doOtherThing()
}

fun doSomething() {}
fun doOtherThing() {}

// expect-error 7:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
