package sample

object Foo {
    @JvmField
    val greeting = "hello"
}

// expect-error 5:9 may-be-constant "Property 'greeting' can be a 'const val' (no autofix for this shape)"
