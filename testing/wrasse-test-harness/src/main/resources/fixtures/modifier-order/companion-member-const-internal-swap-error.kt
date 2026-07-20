package sample

class Foo20 {
    companion object {
        const internal val V = ""
    }
}

// expect-error 5:9 modifier-order "Modifiers out of order, expected: internal const"
