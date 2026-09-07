package sample

fun kind(value: String): Int {
    return when (value) {
        "alphaConditionValueOnes", "bravoConditionValueTwos" -> 1
        else -> 0
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
