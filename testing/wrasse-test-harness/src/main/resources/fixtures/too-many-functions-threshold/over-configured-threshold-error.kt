package sample

class Foo {
    fun a() {}
    fun b() {}
    fun c() {}
    fun d() {}
}

// expect-error 3:7 too-many-functions "Class 'Foo' has 4 functions; the maximum allowed is 3"
