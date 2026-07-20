package sample

fun <T> describe(x: T)where  T:Any,T:Comparable<T> = x.toString()

// expect-error 1:1 format "File is not wrasse-formatted"
