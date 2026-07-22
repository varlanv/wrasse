package sample

val r = 2 until 1 step 2

// expect-error 3:9 invalid-range "This loop will never be executed due to its expression"
