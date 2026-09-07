package sample

fun show(x: String?): String {
    return "${x?.toString()}"
}

// expect-clean
