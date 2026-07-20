package sample

open class Foo {
    public open fun bar() {}
}

// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier"
