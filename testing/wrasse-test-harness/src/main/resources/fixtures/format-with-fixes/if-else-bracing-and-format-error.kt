package sample

class Box {
    fun show(): String {
        if (true)
            return "yes"
        else
            return "no"
    }
}

// expect-error 6:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 8:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 1:1 format "File is not wrasse-formatted"
