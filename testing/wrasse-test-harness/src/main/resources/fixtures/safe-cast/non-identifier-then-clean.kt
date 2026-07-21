package sample

fun numberMagic(number: Number): String? {
    val i = if (number is Int) number.toString() else null
    return i
}

// expect-clean
