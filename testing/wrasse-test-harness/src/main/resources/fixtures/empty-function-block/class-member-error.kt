package sample

class Foo {
    fun bar() {
    }
}

// expect-error 4:15 empty-function-block "Empty function block detected. Empty blocks of code serve no purpose and should be removed"
