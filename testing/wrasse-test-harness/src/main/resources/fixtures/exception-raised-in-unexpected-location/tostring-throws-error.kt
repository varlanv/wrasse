package sample

class Foo {
    override fun toString(): String {
        throw IllegalStateException()
    }
}

// expect-error 4:18 exception-raised-in-unexpected-location "This method is not expected to throw exceptions. This can cause weird behavior."
