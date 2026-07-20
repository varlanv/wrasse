package sample

class Outer {
    public inner class Inner {
        fun bar() {}
    }
}

// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier"
