package sample

object Tables {
    @JvmField
    val v: FloatArray = floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f)

    @JvmField val w = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90)
}

// expect-error 1:1 format "File is not wrasse-formatted"
