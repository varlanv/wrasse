package sample

class Foo {
    suspend internal fun orderOnly() {}
}

// expect-error 4:5 modifier-order "Modifiers out of order, expected: internal suspend"
