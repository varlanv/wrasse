package sample

class Box(val value: Int) {
    fun single(): Int = value + 1

    fun unit(): Unit = println(value)

    fun fail(): Nothing = throw Throwable("boom")

    fun inferred() = value * 2

    fun multi(): Int =
        value +
            3

    fun block(): Int {
        return value
    }
}

fun String.tail() = drop(1)

fun String.head(): Char = first()

// expect-error 21:19 forbidden-expression-body-functions "Function body must be a block, not an expression (no autofix for this shape)"
// expect-error 23:25 forbidden-expression-body-functions "Function body must be a block, not an expression"

// expect-error 4:23 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 6:22 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 8:25 forbidden-expression-body-functions "Function body must be a block, not an expression"
// expect-error 10:20 forbidden-expression-body-functions "Function body must be a block, not an expression (no autofix for this shape)"
// expect-error 12:22 forbidden-expression-body-functions "Function body must be a block, not an expression (no autofix for this shape)"
