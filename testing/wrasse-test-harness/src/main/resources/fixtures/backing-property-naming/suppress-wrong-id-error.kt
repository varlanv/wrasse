package sample

class Foo {
    @Suppress("no-such-rule")
    private val _Bar = 1
}

// expect-error 5:17 backing-property-naming "Backing property should start with underscore followed by lower camel case"
