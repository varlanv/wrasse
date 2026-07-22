package sample

object Foo {
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

// expect-error 3:8 too-many-functions "Object 'Foo' has 12 functions; the maximum allowed is 11"
