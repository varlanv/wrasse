package sample

fun numberMagic(number: Number): Int? = if (number is Int) number else null

// expect-error 3:41 safe-cast "This if/else can be replaced with a safe cast (as?)"
