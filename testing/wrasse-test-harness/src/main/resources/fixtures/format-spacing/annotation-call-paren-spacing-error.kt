package sample

annotation class Ann(val value: String)

@Ann ("marker")
fun noop() = Unit

// expect-error 1:1 format "File is not wrasse-formatted"
