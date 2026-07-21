package sample

fun demo(): Int {
    val negated = - /* comment */ 1
    return negated
}

// expect-error 1:1 format "File is not wrasse-formatted"
