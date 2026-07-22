package sample

class Foo(val firstName: String) {
    constructor(FirstName: String) : this(FirstName)
}

// expect-error 4:17 constructor-parameter-naming "Constructor parameter name should start with a lowercase letter and use camel case"
