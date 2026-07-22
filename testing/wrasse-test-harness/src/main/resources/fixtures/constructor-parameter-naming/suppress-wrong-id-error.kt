package sample

@Suppress("no-such-rule")
class Foo(val FirstName: String)

// expect-error 4:15 constructor-parameter-naming "Constructor parameter name should start with a lowercase letter and use camel case"
