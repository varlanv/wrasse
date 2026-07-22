package sample

class Foo {
    @Suppress("no-semicolons")
    override fun toString(): String {
        throw IllegalStateException()
    }
}

// expect-error 5:18 exception-raised-in-unexpected-location "This method is not expected to throw exceptions. This can cause weird behavior."
