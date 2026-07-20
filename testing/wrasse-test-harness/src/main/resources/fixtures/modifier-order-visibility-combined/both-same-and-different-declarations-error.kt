package sample

class Foo {
    suspend public fun bothProblems() {}
    public fun onlyRedundant() {}
    suspend internal fun onlyOrder() {}
}

// expect-error 4:13 redundant-visibility-modifier "Redundant public visibility modifier"
// expect-error 5:5 redundant-visibility-modifier "Redundant public visibility modifier"
// expect-error 6:5 modifier-order "Modifiers out of order, expected: internal suspend"
