package sample

fun demo() {
    if (true) doSomething() else if (false) doOtherThing() else doThirdThing()
}

fun doSomething() {}
fun doOtherThing() {}
fun doThirdThing() {}

// expect-error 4:15 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 4:45 if-else-bracing "Missing braces on branch of if-statement"
// expect-error 4:65 if-else-bracing "Missing braces on branch of if-statement"
