package sample

fun outer(x: Boolean): Int {
    return run {
        fun inner(): Int {
            return 1
        }
        if (x) return 5
        2
    }
}

// expect-error 5:26 function-expression-body "Function body should be replaced with body expression"
