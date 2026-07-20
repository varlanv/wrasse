package sample

class Foo {
    companion object {
        public fun bar() {}
    }
}

// expect-error 5:9 redundant-visibility-modifier "Redundant public visibility modifier"
