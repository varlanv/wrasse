package sample

fun use(source: Pair<String, String>): String {
    val (first, second) =
                source
    return first + second
}

// expect-error 1:1 format "File is not wrasse-formatted"
