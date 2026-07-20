package sample

class Foo {
    /** Doc. */
    public fun bar() {}
}

// expect-error 5:5 redundant-visibility-modifier "Redundant public visibility modifier"
