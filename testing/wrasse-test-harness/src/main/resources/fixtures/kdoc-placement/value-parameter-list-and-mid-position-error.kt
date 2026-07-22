package sample

class Empty(
    /** some comment */
)

class Foo(val bar: /** doc */ Int)

// expect-error 4:5 kdoc-placement "A KDoc is not allowed inside a 'value_parameter_list'"
// expect-error 7:20 kdoc-placement "A KDoc is allowed only at the start of a 'value_parameter'"
