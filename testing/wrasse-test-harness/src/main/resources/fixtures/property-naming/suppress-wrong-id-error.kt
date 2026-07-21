package sample

class Foo {
    @Suppress("no-such-rule")
    val Bar = 1
}

// expect-error 5:9 property-naming "Property name should start with a lowercase letter and use camel case"
