package sample

annotation class Ann

@Ann
public class Foo

// expect-error 6:1 redundant-visibility-modifier "Redundant public visibility modifier"
