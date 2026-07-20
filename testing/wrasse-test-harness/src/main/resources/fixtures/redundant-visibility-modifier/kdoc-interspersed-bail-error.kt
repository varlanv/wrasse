package sample

annotation class Ann

class Foo {
    @Ann
    /** Doc interspersed inside the modifier list. */
    public fun bar() {}
}

// expect-error 8:5 redundant-visibility-modifier "Redundant public visibility modifier (no autofix for this shape)"
