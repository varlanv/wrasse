package sample

annotation class Bar

class Foo(
    @Bar
    bar: String,
)