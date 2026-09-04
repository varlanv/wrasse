package sample

annotation class ExperimentalApi

object Json {

    fun <T> fromJsonString(serializer: String, jsonString: String): T = throw RuntimeException()

    @OptIn(ExperimentalApi::class)
    fun <T> fromByteArray(
        serializer: String,
        jsonBytes: String,
    ): T = throw RuntimeException()
}

// expect-error 1:1 format "File is not wrasse-formatted"
