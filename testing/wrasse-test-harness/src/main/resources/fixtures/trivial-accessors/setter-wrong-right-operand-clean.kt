package sample

class Foo {
    var other: Int = 0
    var prop: Int = 0
        set(value) {
            field = other
        }
}

// expect-clean
