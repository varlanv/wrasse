package sample

fun <T> describe(x: T) where T : Any, T : Comparable<T> = x.toString()