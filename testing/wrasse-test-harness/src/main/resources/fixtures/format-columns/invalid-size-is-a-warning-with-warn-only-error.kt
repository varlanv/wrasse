package sample

// wrasse columns:0
val xs = arrayOf(1, 2)

// fixture-option: warn-only
// expect-warning 3:1 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
