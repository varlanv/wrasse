package sample

class Foo {
    private val _bar = 1
    private val bar: Int get() = _bar
}

// expect-error 4:17 backing-property-naming "Backing property is only allowed when the matching property or function is public"
