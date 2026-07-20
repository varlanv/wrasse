package sample

fun demo(value: Int ?): String {
    return value?.toString() ?: "none"
}

// expect-error 1:1 format "File is not wrasse-formatted"
