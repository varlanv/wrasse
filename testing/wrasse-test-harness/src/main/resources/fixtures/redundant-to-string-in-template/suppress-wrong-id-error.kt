package sample

@Suppress("no-semicolons")
fun show(x: Int): String {
    return "${x.toString()}"
}

// expect-error 5:15 redundant-to-string-in-template "Redundant '.toString()' call in string template"
