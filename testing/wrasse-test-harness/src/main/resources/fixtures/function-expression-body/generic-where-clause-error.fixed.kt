package sample

fun <T> describe(value: T): String where T : CharSequence = value.toString()