package sample

@Suppress("no-semicolons")
val r = 2..1

// expect-error 4:9 invalid-range "This loop will never be executed due to its expression"
