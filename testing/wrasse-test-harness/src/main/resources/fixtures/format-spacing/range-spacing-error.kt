package sample

fun demo(): List<Int> {
    return (1 .. 5).toList()
}

// expect-error 1:1 format "File is not wrasse-formatted"
