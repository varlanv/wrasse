package sample

fun p(a: String, b: String): Pair<String, String> = a to b

fun use(): String {
    val (first, second) =
        p("left-value-here", "right-value-here")
    return first + second
}

// expect-error 1:1 format "File is not wrasse-formatted"
