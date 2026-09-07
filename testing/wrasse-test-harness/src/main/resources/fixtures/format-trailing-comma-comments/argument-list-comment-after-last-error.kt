package sample

fun two(a: String, b: String): String = a + b

val joined = two(
    "a",
    "b"
    // the closing paren is on its own line
)

// expect-error 1:1 format "File is not wrasse-formatted"
