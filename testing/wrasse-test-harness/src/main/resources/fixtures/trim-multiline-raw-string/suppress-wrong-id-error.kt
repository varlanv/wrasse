package sample

@Suppress("no-semicolons")
val s = """
    Hello
"""

// expect-error 4:9 trim-multiline-raw-string "Multiline raw strings should be followed by trimIndent() or trimMargin()"
