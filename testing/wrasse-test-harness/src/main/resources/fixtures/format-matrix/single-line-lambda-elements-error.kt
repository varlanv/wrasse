package sample

// wrasse matrix:2
val fs = listOf<(Int) -> Int>({ it }, { it + 1 }, { x -> x * 22 }, { 7 })

// expect-error 1:1 format "File is not wrasse-formatted"
