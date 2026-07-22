package sample

interface Greeter {
    class Nested {
        fun greeting(): String = "hi"
    }
}

// expect-error 5:13 function-only-returning-constant "Function 'greeting' only returns a constant; consider declaring a constant instead"
