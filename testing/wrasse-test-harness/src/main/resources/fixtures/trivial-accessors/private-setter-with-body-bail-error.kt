package sample

class Foo {
    var prop: Int = 0
        private set(value) {
            field = value
        }
}

// expect-error 5:9 trivial-accessors "Trivial accessor (no autofix for this shape)"
