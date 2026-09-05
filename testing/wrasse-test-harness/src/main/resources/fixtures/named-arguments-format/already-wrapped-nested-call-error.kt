package sample

class Inner(val a: Int, val b: Int)

class Outer(val inner: Inner, val label: String)

fun build(): Outer =
    Outer(
        Inner(1, 2),
        "label",
    )

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 8:10 named-arguments "Positional arguments should be named"
// expect-error 9:14 named-arguments "Positional arguments should be named"
