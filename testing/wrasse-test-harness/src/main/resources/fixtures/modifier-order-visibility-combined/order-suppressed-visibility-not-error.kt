package sample

class Foo {
    @Suppress("modifier-order")
    suspend internal fun suppressedOrder() {}

    public fun stillReportsRedundant() {}
}

// expect-error 7:5 redundant-visibility-modifier "Redundant public visibility modifier"
