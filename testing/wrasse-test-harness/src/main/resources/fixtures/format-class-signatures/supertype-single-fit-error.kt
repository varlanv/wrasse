package sample

interface VeryLongSuperTypeName

class Foo(a: Int) : VeryLongSuperTypeName

// expect-error 1:1 format "File is not wrasse-formatted"
