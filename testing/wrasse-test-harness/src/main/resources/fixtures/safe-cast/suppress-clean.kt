package sample

@Suppress("safe-cast")
fun numberMagic(number: Number): Int? {
    val i = if (number is Int) number else null
    return i
}

// expect-clean
