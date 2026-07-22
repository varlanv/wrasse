package sample

class Foo {
    protected fun stuff() {
    }
}

// expect-error 4:27 empty-function-block "Empty function block detected. Empty blocks of code serve no purpose and should be removed"
