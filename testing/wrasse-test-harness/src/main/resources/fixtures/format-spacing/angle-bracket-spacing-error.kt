package sample

fun demo(list: List< Int >, threshold: Int): Boolean {
    return threshold < list.size
}

// expect-error 1:1 format "File is not wrasse-formatted"
