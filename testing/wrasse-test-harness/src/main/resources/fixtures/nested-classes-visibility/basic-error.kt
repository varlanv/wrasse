package sample

internal class Outer {
    public class Nested
}

// expect-error 4:5 nested-classes-visibility "The explicit 'public' modifier still results in an internal nested class"
