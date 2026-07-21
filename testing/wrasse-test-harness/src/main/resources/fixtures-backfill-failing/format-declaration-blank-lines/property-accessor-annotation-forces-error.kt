package sample

annotation class Ann1

var foo: Boolean = false
    get() = field
    @Ann1
    set(value) {
        field = value
    }

// expect-error 1:1 format "File is not wrasse-formatted"
