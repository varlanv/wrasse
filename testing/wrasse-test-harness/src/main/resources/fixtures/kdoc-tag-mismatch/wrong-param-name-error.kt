package sample

/**
 * @param someParam the value
 */
fun myFun(otherParam: String) {
    println(otherParam)
}

// expect-error 6:5 kdoc-tag-mismatch "Documentation of myFun is outdated: documented parameters 'someParam' are not present in the declaration"
