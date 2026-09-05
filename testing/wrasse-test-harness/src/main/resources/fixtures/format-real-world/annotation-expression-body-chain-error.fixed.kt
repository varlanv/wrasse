package sample

annotation class ExperimentalApi

class Codec {
    fun <T> decode(input: String): T = throw RuntimeException()
}

object Json {
    private val codec = Codec()

    @OptIn(ExperimentalApi::class)
    fun <T> fromStream(input: String): T = codec.decode(input)
}