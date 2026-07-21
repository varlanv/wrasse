package sample

class Foo {
    @Suppress("no-such-rule")
    fun bar() {
        val a = 1
        val b = 5
        val r = a.rangeTo(b)
    }
}

// expect-error 8:17 range-conventional "Replace rangeTo call with the .. operator"
