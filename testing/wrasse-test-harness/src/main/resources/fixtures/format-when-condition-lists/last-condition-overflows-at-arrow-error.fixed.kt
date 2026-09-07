package sample

fun kind(value: String): Int {
    return when (value) {
        "alphaConditionValueOnes",
        "bravoConditionValueTwos" -> 1
        else -> 0
    }
}