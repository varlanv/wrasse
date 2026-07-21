package sample

class Foo

fun Foo(): Foo {
    return Foo()
}

// expect-clean
