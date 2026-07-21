package sample

annotation class Bar

class Foo(@Bar
    bar: String)

// expect-error 1:1 format "File is not wrasse-formatted"
