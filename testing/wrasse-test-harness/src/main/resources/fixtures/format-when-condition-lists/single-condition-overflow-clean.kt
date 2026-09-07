package sample

class DoubleColumnTypeWithAnExceedinglyLongName

fun kind(value: Any): Int {
    return when (value) {
        is DoubleColumnTypeWithAnExceedinglyLongName -> 1
        else -> 0
    }
}

// fixture-option: trailing-newline
// expect-clean
