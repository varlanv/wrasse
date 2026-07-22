package sample

class Foo {
    @Suppress("no-such-rule")
    init {
    }
}

// expect-error 5:10 empty-init-block "Empty init block detected. Empty blocks of code serve no purpose and should be removed"
