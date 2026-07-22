package sample

internal class Outer {
    public interface A
    public object B
    public class C
}

// expect-error 4:5 nested-classes-visibility "The explicit 'public' modifier still results in an internal nested class"
// expect-error 5:5 nested-classes-visibility "The explicit 'public' modifier still results in an internal nested class"
// expect-error 6:5 nested-classes-visibility "The explicit 'public' modifier still results in an internal nested class"
