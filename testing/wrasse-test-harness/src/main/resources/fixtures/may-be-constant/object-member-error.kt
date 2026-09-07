package sample

object Foo {
    val greeting = "hello"
}

// expect-error 4:9 may-be-constant "Property 'greeting' can be a 'const val'"
