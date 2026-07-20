package sample

fun demo(strings: List<String>): List<Int> {
    return strings.map(String:: length)
}

// expect-error 1:1 format "File is not wrasse-formatted"
