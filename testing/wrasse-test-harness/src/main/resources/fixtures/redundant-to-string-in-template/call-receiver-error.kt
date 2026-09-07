package sample

fun compute(): Int = 42

fun show(): String {
    return "${compute().toString()}"
}

// expect-error 6:15 redundant-to-string-in-template "Redundant '.toString()' call in string template"
