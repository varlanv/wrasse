package sample

val s = """
    Hello
""".length

// expect-error 3:9 trim-multiline-raw-string "Multiline raw strings should be followed by trimIndent() or trimMargin()"
