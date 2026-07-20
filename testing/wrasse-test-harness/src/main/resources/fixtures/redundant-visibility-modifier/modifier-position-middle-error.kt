package sample

open class Foo {
    open public suspend fun bar() {}
}

// expect-error 4:10 redundant-visibility-modifier "Redundant public visibility modifier"
