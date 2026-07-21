package sample

@Suppress("no-semicolons")
fun numberMagic(number: Number): Int? {
    val i = if (number is Int) number else null
    return i
}

// expect-error 5:13 safe-cast "This if/else can be replaced with a safe cast (as?)"
