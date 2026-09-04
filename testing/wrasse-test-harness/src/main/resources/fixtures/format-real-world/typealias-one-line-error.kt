package sample

annotation class Serializable(val with: String = "")

typealias FlexibleDecimal =
    @Serializable(with = "FlexibleBigDecimalSerializer")
    String

// expect-error 1:1 format "File is not wrasse-formatted"
