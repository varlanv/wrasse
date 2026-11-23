package sample

class Row(vararg val cells: Int)

// wrasse columns:2
val r = Row(1, 2, 3)

// expect-error 1:1 format "File is not wrasse-formatted"
