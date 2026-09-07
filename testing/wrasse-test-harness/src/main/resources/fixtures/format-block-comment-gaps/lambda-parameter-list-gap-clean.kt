package sample

val add: (Int, Int) -> Int = { /* head */ first, second ->
    first + second
}

val negate: (Int) -> Int = { value /* tail */ ->
    -value
}

// expect-clean
