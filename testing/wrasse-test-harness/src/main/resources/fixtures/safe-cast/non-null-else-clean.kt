package sample

fun numberMagic(number: Number): Any {
    val i = if (number is Int) number else String()
    return i
}

// expect-clean
