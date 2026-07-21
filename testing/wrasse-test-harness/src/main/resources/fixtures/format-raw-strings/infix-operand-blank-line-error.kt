package sample

fun crashing(): Pair<String, String> {
    val crashingSource = "sample/Sample.kt" to
        """
        package sample

        private val marker = 0
        """.trimIndent()
    return crashingSource
}

// expect-error 1:1 format "File is not wrasse-formatted"
