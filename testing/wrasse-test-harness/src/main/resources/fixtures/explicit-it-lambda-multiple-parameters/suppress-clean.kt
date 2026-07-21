package sample

@Suppress("explicit-it-lambda-multiple-parameters")
fun demo(list: List<Int>) {
    list.zipWithNext { it, next -> it + next }
}

// expect-clean
