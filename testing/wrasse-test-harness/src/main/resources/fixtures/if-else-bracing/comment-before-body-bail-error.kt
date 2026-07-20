package sample

fun demo() {
    if (true) // comment
        doSomething()
    else
        doOtherThing()
}

fun doSomething() {}
fun doOtherThing() {}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement (no autofix for this shape)"
// expect-error 7:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
