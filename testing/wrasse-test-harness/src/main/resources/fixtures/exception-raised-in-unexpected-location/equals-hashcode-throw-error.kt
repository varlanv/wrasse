package sample

class Foo {
    override fun equals(other: Any?): Boolean {
        throw IllegalStateException()
    }

    override fun hashCode(): Int {
        throw IllegalStateException()
    }
}

// expect-error 4:18 exception-raised-in-unexpected-location "This method is not expected to throw exceptions. This can cause weird behavior."
// expect-error 8:18 exception-raised-in-unexpected-location "This method is not expected to throw exceptions. This can cause weird behavior."
