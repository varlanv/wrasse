package sample

fun first(
    alphaParameterName: List<Int>,
    betaParameterName: List<Int>,
    gammaParameterName: List<Int>,
    delta: Int,
): Int = delta

fun middle(a: List<Int>, b: List<Int>, path: Int): Int = a.size + b.size + path

fun last(
    alphaParameterName: List<Int>,
    betaParameterName: List<Int>,
    gammaParameterName: List<Int>,
    delta: Int,
): Int = delta

// fixture-option: trailing-newline
// expect-clean
