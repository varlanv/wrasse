package sample

fun two(a: String, b: String): String = a + b

val joined = two(
    a = "a",
    b = "b"
    // b is the tail
)

// expect-error 1:1 format "File is not wrasse-formatted"
