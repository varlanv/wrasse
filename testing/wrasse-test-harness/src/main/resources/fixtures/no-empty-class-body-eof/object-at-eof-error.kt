package sample

object Outer {
}

// expect-error 1:1 trailing-newline "File must end with a newline"
// expect-error 3:14 no-empty-class-body "Empty class body"
