package sample

class Foo {
    public fun bar(): Int = 1
}

// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier"
