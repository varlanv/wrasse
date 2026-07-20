package sample

class Foo {
    @Suppress("modifier-order")
    suspend internal fun suppressedOrder() {}

    fun stillReportsRedundant() {}
}