package sample

class Holder {
    val table = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

    fun names() = listOf("alpha", "beta", "gamma", "delta", "epsilon")
}

// expect-error 1:1 format "File is not wrasse-formatted"
