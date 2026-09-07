package sample

class Foo {
    fun a() {}
    fun b() {}
    fun c() {}
    fun d() {}
}

// expect-error 3:7 large-class "Class 'Foo' is too large (6 lines); the maximum allowed is 5"
