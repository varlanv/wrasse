package sample

class Outer {
    inner public open class Foo
}

// expect-error 4:11 redundant-visibility-modifier "Redundant public visibility modifier"
// expect-error 4:5 modifier-order "Modifiers out of order, expected: open inner"
