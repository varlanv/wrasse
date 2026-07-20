package sample

abstract @Deprecated("test") open class Foo5

// expect-error 3:1 modifier-order "Modifiers out of order, expected: open abstract"
