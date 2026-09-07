package sample

fun show(x: Int): String {
    return "${x.toString()}_"
}

// expect-error 4:15 redundant-to-string-in-template "Redundant '.toString()' call in string template"
