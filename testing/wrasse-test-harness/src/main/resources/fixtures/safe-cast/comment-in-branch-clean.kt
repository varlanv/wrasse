package sample

fun numberMagic(number: Number): Int? {
    val i = if (number is Int) { number /* keep */ } else null
    return i
}

// expect-clean
