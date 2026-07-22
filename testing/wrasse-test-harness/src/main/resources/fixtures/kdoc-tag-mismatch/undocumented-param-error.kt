package sample

/**
 * @param first
 */
fun myFun(first: String, second: String) {
    println(first + second)
}

// expect-error 6:5 kdoc-tag-mismatch "Documentation of myFun is outdated: parameters 'second' are not documented"
