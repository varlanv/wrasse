package sample

fun payloadWithBlankPaddedLines(): String {
    val text = """
        header
                                    
        body
        """
        .trimIndent()
    return text
}

// expect-clean
