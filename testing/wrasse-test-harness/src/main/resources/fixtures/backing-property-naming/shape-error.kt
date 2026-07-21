package sample

class Foo {
    private val _Bar = 1
}

// expect-error 4:17 backing-property-naming "Backing property should start with underscore followed by lower camel case"
