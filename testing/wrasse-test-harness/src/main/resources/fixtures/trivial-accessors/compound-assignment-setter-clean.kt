package sample

class Foo {
    var prop: Int = 0
        set(value) {
            field += value
        }
}

// expect-clean
