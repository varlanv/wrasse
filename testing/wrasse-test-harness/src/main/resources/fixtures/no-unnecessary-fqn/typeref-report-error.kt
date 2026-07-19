package sample

// fixture-aux-file: aux/Aux.kt

val w: sample.aux.Widget = TODO()

// expect-error 4:8 no-unnecessary-fqn "Unnecessary fully qualified name"
