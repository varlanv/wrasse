package sample

class Foo {
    var prop: Int = 0
        set(value) { field = transform(value) }
}

fun transform(x: Int): Int = x

// expect-clean
