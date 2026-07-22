package sample

/**
 * @param second
 * @param first
 */
fun myFun(first: String, second: String) {
    println(first + second)
}

// expect-error 7:5 kdoc-tag-mismatch "Documentation of myFun is outdated: order of documented parameters does not match the declaration order"
