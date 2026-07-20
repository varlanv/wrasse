package sample

class Foo {
    // leading comment, not part of the modifier list
    public fun bar() {}
}

// expect-error 5:5 redundant-visibility-modifier "Redundant public visibility modifier"
