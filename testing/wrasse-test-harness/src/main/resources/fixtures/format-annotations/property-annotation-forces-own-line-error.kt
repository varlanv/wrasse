package sample

annotation class WithArg(val v: String)

@WithArg("x") val a: Int = 1

// expect-error 1:1 format "File is not wrasse-formatted"
