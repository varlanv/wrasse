package sample

class Foo {
    public val x: Int = 1
}

// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier"
