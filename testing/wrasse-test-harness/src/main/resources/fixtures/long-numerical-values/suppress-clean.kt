package sample

class Foo {
    @Suppress("long-numerical-values")
    val x = 1000000
}

// expect-clean
