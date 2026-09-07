package sample

fun numberMagic(number: Number): Int? {
    val i = if (/* keep */ number is Int) number else null
    return i
}

// expect-error 4:13 safe-cast "This if/else can be replaced with a safe cast (as?) (no autofix for this shape)"
