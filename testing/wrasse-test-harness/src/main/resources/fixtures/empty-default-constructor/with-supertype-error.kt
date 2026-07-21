package sample

open class Bar

class Foo() : Bar()

// expect-error 5:10 empty-default-constructor "Empty default constructor"
