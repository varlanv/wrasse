package sample

class Foo1(vararg private val strings: String)

// expect-error 3:12 modifier-order "Modifiers out of order, expected: private vararg"
