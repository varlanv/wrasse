package sample

annotation class Foo1

annotation class Foo2

annotation class Foo3

@Foo1
@Foo2 @Foo3
fun foo() {}

// expect-error 1:1 format "File is not wrasse-formatted"
