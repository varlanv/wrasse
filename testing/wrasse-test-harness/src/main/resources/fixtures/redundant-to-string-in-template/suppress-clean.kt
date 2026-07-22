package sample

@Suppress("redundant-to-string-in-template")
fun show(x: Int): String {
    return "${x.toString()}"
}

// expect-clean
