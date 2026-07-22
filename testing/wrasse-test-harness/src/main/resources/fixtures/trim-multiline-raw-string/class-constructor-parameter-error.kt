package sample

class Foo(
    val s: String =
        """
            Given something
            When something
            Then something
        """
)

// expect-error 5:9 trim-multiline-raw-string "Multiline raw strings should be followed by trimIndent() or trimMargin()"
