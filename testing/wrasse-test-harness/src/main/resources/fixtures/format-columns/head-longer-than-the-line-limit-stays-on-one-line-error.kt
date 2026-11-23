package sample

// wrasse columns:2
val aVeryLongPropertyNameThatPassesTheLimit: IntArray = intArrayOf(1, 2, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
