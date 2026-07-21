package sample

class Outer {
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

    class Inner {
        fun g1() {}
        fun g2() {}
        fun g3() {}
        fun g4() {}
        fun g5() {}
        fun g6() {}
        fun g7() {}
        fun g8() {}
        fun g9() {}
        fun g10() {}
        fun g11() {}
        fun g12() {}
    }
}

// expect-error 16:11 too-many-functions "Class 'Inner' has 12 functions; the maximum allowed is 11"
