package sample

fun numberMagic(number: Number): Int? {
    val i = if (number !is Int) null else number
    return i
}

// expect-error 4:13 safe-cast "This if/else can be replaced with a safe cast (as?)"
