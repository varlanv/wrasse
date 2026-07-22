package sample

fun foo(s: String) {}

val bar = foo(
    """
        Given something
        When something
        Then something
    """
)

// expect-error 6:5 trim-multiline-raw-string "Multiline raw strings should be followed by trimIndent() or trimMargin()"
