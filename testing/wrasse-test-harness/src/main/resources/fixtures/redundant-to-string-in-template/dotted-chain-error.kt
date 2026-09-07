package sample

class Point(val x: Int)

fun show(point: Point): String {
    return "${point.x.toString()}"
}

// expect-error 6:15 redundant-to-string-in-template "Redundant '.toString()' call in string template"
