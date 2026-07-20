package sample

public interface Foo {
    public fun bar()
}

// expect-error 3:1 redundant-visibility-modifier "Redundant public visibility modifier"
// expect-error 4:5 redundant-visibility-modifier "Redundant public visibility modifier"
