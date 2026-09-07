package sample

fun pick(one: String, two: String): String = one + two

fun kind(value: String): Int {
    return when (value) {
        pick(
            "alphaColumnValueName",
            "bravoColumnValueName",
        ) -> 1
        else -> 0
    }
}