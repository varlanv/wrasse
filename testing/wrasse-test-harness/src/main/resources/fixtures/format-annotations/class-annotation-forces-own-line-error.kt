package sample

annotation class WithArg(val v: String)

@WithArg("x") class Foo

// expect-error 1:1 format "File is not wrasse-formatted"
