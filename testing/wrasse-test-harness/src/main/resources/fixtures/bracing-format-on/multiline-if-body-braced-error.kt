package sample

fun demo() {
    if (true)
        50
            .toString()
    else
        doOtherThing()
}

fun doOtherThing() {}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 8:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 1:1 format "File is not wrasse-formatted"
