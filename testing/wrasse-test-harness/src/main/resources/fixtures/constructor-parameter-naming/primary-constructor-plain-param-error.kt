package sample

class Foo(FirstName: String) {
    val name = FirstName
}

// expect-error 3:11 constructor-parameter-naming "Constructor parameter name should start with a lowercase letter and use camel case"
