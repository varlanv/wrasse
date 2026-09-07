package sample

fun two(a: String, b: String): String = a + b

val joined = two(
    "a",
    "b",
    // "z",
)

// fixture-option: trailing-newline
// expect-clean
