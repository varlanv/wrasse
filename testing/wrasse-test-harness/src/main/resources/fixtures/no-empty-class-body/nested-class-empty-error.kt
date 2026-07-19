package sample

class Outer {
    class Inner {
    }
}

// expect-error 4:17 no-empty-class-body "Empty class body"
