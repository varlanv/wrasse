package sample

// fixture-aux-file: aux/Aux.kt

val w1: sample.aux.Widget = TODO()
val w2: sample.aux.Widget = TODO()

// expect-error 4:9 no-unnecessary-fqn "Unnecessary fully qualified name"
// expect-error 5:9 no-unnecessary-fqn "Unnecessary fully qualified name"
