package sample

class Box

fun Box.show(): String {
    return "${this.toString()}"
}

// expect-error 6:15 redundant-to-string-in-template "Redundant '.toString()' call in string template (no autofix for this shape)"
