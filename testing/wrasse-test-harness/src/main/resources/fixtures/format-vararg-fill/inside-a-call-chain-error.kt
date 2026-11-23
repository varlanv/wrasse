package sample

val doubled = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100).map { it * 2 }.filter { it > 50 }

// expect-error 1:1 format "File is not wrasse-formatted"
