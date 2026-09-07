package sample

val supplier: () -> Nothing = {
    throw IllegalStateException("bad")
}

// expect-clean
