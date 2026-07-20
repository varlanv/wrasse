package sample

class Foo {
    public // trailing comment
    fun bar() {}
}

// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier (no autofix for this shape)"
