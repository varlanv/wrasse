package sample

// wrasse matrix:2
val xs = listOf(
    """a
b""",
    "c",
)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' needs every element on one line, but an element of the call below must stay on several lines"
