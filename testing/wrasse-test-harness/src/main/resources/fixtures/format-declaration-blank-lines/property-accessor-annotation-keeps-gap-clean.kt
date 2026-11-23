package sample

annotation class Ann1

var foo: Boolean = false
    get() = field
    @Ann1
    set(value) {
        field = value
    }

var bar: Boolean = false
    get() {
        return field
    }
    @Ann1 set(value) {
        field = value
    }

// expect-clean
