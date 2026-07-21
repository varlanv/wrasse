package sample

class Foo {
    companion object {
        const val barBaz = 1
    }
}

// expect-error 5:19 property-naming "Property name should use the SCREAMING_SNAKE_CASE notation when the value can not be changed"
