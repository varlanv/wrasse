package sample

/**
 * @param someParam the value
 */
@Suppress("no-semicolons")
fun myFun(otherParam: String) {
    println(otherParam)
}

// expect-error 7:5 kdoc-tag-mismatch "Documentation of myFun is outdated: documented parameters 'someParam' are not present in the declaration"
