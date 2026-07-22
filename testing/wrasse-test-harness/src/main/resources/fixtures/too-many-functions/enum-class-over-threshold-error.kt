package sample

enum class Foo {
    A;

    fun f1() {}
    fun f2() {}
    fun f3() {}
    fun f4() {}
    fun f5() {}
    fun f6() {}
    fun f7() {}
    fun f8() {}
    fun f9() {}
    fun f10() {}
    fun f11() {}
    fun f12() {}
}

// expect-error 3:12 too-many-functions "Enum class 'Foo' has 12 functions; the maximum allowed is 11"
