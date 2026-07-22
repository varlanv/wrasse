package sample

val r = 1 downTo 2

// expect-error 3:9 invalid-range "This loop will never be executed due to its expression"
