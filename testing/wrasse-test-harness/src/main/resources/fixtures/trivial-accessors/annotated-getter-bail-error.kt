package sample

class Foo {
    val prop: Int = 0
        @JvmName("getProp")
        get() = field
}

// expect-error 5:9 trivial-accessors "Trivial accessor (no autofix for this shape)"
