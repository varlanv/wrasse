package sample

fun bothMissing(first: Int?, second: Int?): Boolean {
    return first == null
        && second == null
}

// expect-error 1:1 format "File is not wrasse-formatted"
