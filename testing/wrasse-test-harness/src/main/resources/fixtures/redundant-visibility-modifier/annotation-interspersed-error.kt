package sample

annotation class Ann

class Foo {
    @Ann public fun bar() {}
}

// expect-error 6:10 redundant-visibility-modifier "Redundant public visibility modifier"
