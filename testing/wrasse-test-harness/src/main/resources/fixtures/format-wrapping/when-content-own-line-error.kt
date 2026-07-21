package sample

fun example(x: Int): Int {
    return when (x) { 1 -> 10
        else -> 20 }
}

// expect-error 1:1 format "File is not wrasse-formatted"
