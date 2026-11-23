package sample

val first = 1
val second = 2
val third = 3
val fourth = 4
val fifth = 5

val all = listOf(first, second, third, fourth, fifth, first, second, third, fourth, fifth)

// expect-error 1:1 format "File is not wrasse-formatted"
