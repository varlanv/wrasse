package sample

class Box(val value: Int) {
    fun mapped(items: List<Int>): List<Int> = items.map {
        it + value
    }

    fun single(): Int = value + 1
}

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 4:45 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 8:23 forbidden-expression-body-functions "Function body must be a block, not an expression"
