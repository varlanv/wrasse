package sample

fun demo(values: List<Map<String, *> >): Int {
    return values.size
}

// expect-error 1:1 format "File is not wrasse-formatted"
