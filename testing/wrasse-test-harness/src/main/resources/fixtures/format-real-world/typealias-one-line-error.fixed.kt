package sample

annotation class Serializable(val with: String = "")

typealias FlexibleDecimal = @Serializable(with = "FlexibleBigDecimalSerializer") String