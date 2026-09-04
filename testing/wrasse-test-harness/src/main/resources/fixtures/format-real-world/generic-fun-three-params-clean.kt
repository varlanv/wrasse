package sample

fun <T> toJsonStream(
    serializer: T,
    obj: T,
    outputName: String,
) {
    println("$serializer $obj $outputName")
}

// expect-clean
