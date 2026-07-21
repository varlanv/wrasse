package sample

val a = 1
val b = 5
val r = a.rangeTo(/* c */ b)

// expect-error 5:9 range-conventional "Replace rangeTo call with the .. operator (no autofix for this shape)"
