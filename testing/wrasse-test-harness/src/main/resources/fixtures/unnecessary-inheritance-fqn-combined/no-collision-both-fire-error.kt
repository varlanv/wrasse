package sample

class Foo : Any() {}

fun g(): kotlin.String = "x"

// expect-error 3:13 unnecessary-inheritance "Unnecessary inheritance of 'Any'"
// expect-error 5:10 no-unnecessary-fqn "Unnecessary fully qualified name"
