package sample

internal enum class Outer {
    A,
    ;

    public class Nested
}

// expect-error 7:5 nested-classes-visibility "The explicit 'public' modifier still results in an internal nested class"
