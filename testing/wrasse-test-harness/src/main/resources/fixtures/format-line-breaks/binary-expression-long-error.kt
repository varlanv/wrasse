package sample

fun demo() {
    val x = "first quite long string literal" + "second quite long literal"
}

// expect-error 1:1 format "File is not wrasse-formatted"
