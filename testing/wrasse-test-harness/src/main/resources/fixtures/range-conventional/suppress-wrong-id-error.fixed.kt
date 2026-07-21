package sample

class Foo {
    @Suppress("no-such-rule")
    fun bar() {
        val a = 1
        val b = 5
        val r = a..b
    }
}