package sample

// wrasse columns:2
@field:Suppress("unused")
@get:JvmName("table")
val xs = intArrayOf(1, 22, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
