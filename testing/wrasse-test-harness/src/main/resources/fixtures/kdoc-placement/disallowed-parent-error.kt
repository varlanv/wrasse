package sample

fun foo() = 1

val result = foo(
    /** some comment */
)

// expect-error 6:5 kdoc-placement "A KDoc is not allowed inside a 'value_argument_list'"
