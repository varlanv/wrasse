package sample

fun numberMagic(number: Int, other: Int): Int? {
    val i = if (number == other) number else null
    return i
}

// expect-clean
