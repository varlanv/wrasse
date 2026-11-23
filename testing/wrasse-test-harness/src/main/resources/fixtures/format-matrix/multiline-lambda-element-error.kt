package sample

// wrasse matrix:2
val xs = listOf<() -> Int>(
    { 1 },
    {
        val a = 2
        a
    },
)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' needs every element on one line, but an element of the call below must stay on several lines"
