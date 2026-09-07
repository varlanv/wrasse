package sample

interface Foo {
    companion object {
        val greeting = "hello"
    }
}

// expect-error 5:13 may-be-constant "Property 'greeting' can be a 'const val'"
