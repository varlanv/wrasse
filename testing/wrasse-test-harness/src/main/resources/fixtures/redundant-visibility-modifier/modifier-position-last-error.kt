package sample

class Foo {
    suspend public fun bar() {}
}

// expect-error 4:13 redundant-visibility-modifier "Redundant public visibility modifier"
