package sample

class Foo {
    var prop: Int = 0
        get() = field
        set(value) {
            field = value
        }
}

// expect-error 5:9 trivial-accessors "Trivial accessor"
// expect-error 6:9 trivial-accessors "Trivial accessor"
