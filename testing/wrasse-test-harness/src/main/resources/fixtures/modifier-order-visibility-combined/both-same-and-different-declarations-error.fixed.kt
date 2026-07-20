package sample

class Foo {
    suspend fun bothProblems() {}
    fun onlyRedundant() {}
    internal suspend fun onlyOrder() {}
}