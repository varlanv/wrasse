package sample

class Outer {
    class Inner {
        public fun bar() {}
    }
}

// expect-error 5:9 redundant-visibility-modifier "Redundant public visibility modifier"
