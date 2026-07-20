package sample

fun demo(a: Int, b: Int): Int {
    val negated = - a
    var counter = b
    counter ++
    return negated - counter
}

// expect-error 1:1 format "File is not wrasse-formatted"
