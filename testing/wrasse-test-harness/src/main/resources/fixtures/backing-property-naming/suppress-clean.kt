package sample

class Foo {
    @Suppress("backing-property-naming")
    private val _Bar = 1
}

// expect-clean
