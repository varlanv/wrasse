package sample

fun act() {}

fun example(a: Boolean, b: Boolean) {
    val firstVeryLongConditionName = a
    val secondVeryLongConditionName = b
    if (firstVeryLongConditionName && secondVeryLongConditionName) {
        act()
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
