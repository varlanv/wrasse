package sample

fun demo() {
    if (true) {
        doSomething()
    } else if (false) doOtherThing() else doThirdThing()
}

fun doSomething() {}
fun doOtherThing() {}
fun doThirdThing() {}

// expect-error 6:23 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 6:43 if-else-bracing "Missing braces on branch of multi-line if-statement"
