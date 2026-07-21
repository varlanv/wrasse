package sample

class Foo {
    var prop: Int = 0
    var otherField: Int = 0
        set(value) {
            otherField = value
        }
}

// expect-clean
