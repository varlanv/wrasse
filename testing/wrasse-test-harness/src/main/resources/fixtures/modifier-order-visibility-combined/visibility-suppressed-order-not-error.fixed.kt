package sample

class Foo {
    @Suppress("redundant-visibility-modifier")
    public fun suppressedRedundant() {}

    internal suspend fun stillReportsOrder() {}
}