package sample

class DoubleColumnType

class StringColumnType

class CharacterColumnType

fun kind(value: Any): Int {
    return when (value) {
        is DoubleColumnType,
        is StringColumnType,
        is CharacterColumnType -> 1
        else -> 0
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
