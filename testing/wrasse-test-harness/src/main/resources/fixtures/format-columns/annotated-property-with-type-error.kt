package sample

typealias Floats = FloatArray

object Tables {
    // wrasse columns:4
    @JvmField internal val weights: Floats = floatArrayOf(0.0f, 0.0f, 1.0f, 0.0f, 0.1f, 0.22f, 0.45f, 0.33f)
}

// expect-error 1:1 format "File is not wrasse-formatted"
