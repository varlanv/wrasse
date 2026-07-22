package sample

class Foo {
    init {
    }
}

// expect-error 4:10 empty-init-block "Empty init block detected. Empty blocks of code serve no purpose and should be removed"
