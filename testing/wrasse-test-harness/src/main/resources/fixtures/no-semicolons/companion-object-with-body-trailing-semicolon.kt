package sample

class A {
    companion object {
        const val emptyString = ""
    };
}

// expect-error 6:6 no-semicolons "Unnecessary semicolon"
