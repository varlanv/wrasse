package sample

fun demo() {
    val x = "quite a long string literal here".padStart(10).reversed()
}

// expect-error 1:1 format "File is not wrasse-formatted"
