package sample

class Foo {
    protected fun finalize() {
        throw IllegalStateException()
    }
}

// expect-error 4:19 exception-raised-in-unexpected-location "This method is not expected to throw exceptions. This can cause weird behavior."
