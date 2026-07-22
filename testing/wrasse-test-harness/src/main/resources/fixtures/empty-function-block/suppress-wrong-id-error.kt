package sample

class Foo {
    @Suppress("no-such-rule")
    fun bar() {
    }
}

// expect-error 5:15 empty-function-block "Empty function block detected. Empty blocks of code serve no purpose and should be removed"
