package sample

fun demo() {
    if (true)
        doSomething()
    else if (false)
        doOtherThing()
    else
        doThirdThing()
}

fun doSomething() {}
fun doOtherThing() {}
fun doThirdThing() {}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 7:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 9:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
