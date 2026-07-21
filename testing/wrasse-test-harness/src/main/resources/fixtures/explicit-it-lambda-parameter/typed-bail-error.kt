package sample

fun use() {
    val lambda = { it: Int -> it.toString() }
    lambda(1)
}

// expect-error 4:20 explicit-it-lambda-parameter "Explicit 'it' lambda parameter with a declared type may not be safely inferred if removed (no autofix for this shape)"
