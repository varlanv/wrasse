package sample

annotation class WithArg(val v: String)

@WithArg("x")fun a() {}

// expect-error 1:1 format "File is not wrasse-formatted"
