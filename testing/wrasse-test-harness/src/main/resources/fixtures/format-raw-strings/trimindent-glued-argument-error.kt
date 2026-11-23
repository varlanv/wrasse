package sample

fun run(name: String, script: String): String = name + script

fun build(): String {
    return run(
        "app",
        """
            kotlin {
            }
            """
            .trimIndent(),
    )
}

// expect-error 1:1 format "File is not wrasse-formatted"
