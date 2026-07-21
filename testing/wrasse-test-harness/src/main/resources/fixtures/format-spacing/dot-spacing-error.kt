package sample

fun demo(text: String, numbers: List<Int>): Int {
    val length = text .length
    return length + numbers. first()
}

// expect-error 1:1 format "File is not wrasse-formatted"
