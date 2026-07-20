package sample

class Foo2 {
    internal val x: Int
        inline internal get() = 1
}

// expect-error 5:9 modifier-order "Modifiers out of order, expected: internal inline"
