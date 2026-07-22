package sample

class Foo(val FirstName: String)

// expect-error 3:15 constructor-parameter-naming "Constructor parameter name should start with a lowercase letter and use camel case"
