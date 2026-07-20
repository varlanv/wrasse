package sample

fun demo() {
    if (outer)
        if (inner)
            doSomething()
        else
            doOtherThing()
}

val outer = true
val inner = false
fun doSomething() {}
fun doOtherThing() {}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement (no autofix for this shape)"
// expect-error 6:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 8:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
