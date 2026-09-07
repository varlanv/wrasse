package sample

fun show(a: Int, b: Int): String {
    return "${a.toString()} and ${b.toString()}"
}

// expect-error 4:15 redundant-to-string-in-template "Redundant '.toString()' call in string template"
// expect-error 4:35 redundant-to-string-in-template "Redundant '.toString()' call in string template"
