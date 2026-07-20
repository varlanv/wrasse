package sample

class Foo {
    @Suppress("redundant-visibility-modifier")
    public fun suppressedRedundant() {}

    suspend internal fun stillReportsOrder() {}
}

// expect-error 7:5 modifier-order "Modifiers out of order, expected: internal suspend"
