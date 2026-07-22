package sample

class Foo {
    @Suppress("exception-raised-in-unexpected-location")
    override fun toString(): String {
        throw IllegalStateException()
    }
}

// expect-clean
