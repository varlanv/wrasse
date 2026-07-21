package sample

fun act() {}

fun example(a: Boolean, b: Boolean) {
    val firstVeryLongConditionName = a
    val secondVeryLongConditionName = b
    if (firstVeryLongConditionName &&
        secondVeryLongConditionName) {
        act()
    }
}