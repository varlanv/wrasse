package sample

val a = 1
val b = 5
val r = a.rangeTo(b)

// expect-error 5:9 range-conventional "Replace rangeTo call with the .. operator"
// expect-error 1:1 format "File is not wrasse-formatted"
