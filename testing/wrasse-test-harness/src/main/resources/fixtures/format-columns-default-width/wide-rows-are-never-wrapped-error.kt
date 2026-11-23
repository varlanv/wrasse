package sample

// wrasse columns:2
val names = listOf("an element that is quite long, longer than half of the default limit", "another element that is also long enough", "c", "d")

// expect-error 1:1 format "File is not wrasse-formatted"
