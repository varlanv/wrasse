package sample

open class Bar(param: String)
open class Base

class Foo(text: String) : Bar (text)

class Qux : Base {
    constructor(text: String) : super ()
}

class Baz constructor ()

// expect-error 1:1 format "File is not wrasse-formatted"
