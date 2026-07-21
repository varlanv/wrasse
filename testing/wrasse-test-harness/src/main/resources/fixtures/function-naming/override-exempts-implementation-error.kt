package sample

interface Api {
    fun Foo()
}

class Impl : Api {
    override fun Foo() {
    }
}

// expect-error 4:9 function-naming "Function name should start with a lowercase letter (except factory methods) and use camel case"
