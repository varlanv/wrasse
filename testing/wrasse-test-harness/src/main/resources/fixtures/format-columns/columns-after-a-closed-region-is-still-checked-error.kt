package sample

// wrasse fmt:off
val   y = 1
// wrasse fmt:on

// wrasse columns:0
val xs = arrayOf(1, 2, 3, 4)

// expect-error 7:1 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
