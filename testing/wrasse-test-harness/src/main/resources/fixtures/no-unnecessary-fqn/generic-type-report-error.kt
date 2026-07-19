package sample

// fixture-aux-file: aux/Aux.kt

val b: sample.aux.Box<Int> = sample.aux.Box(1)

// expect-error 4:8 no-unnecessary-fqn "Unnecessary fully qualified name"
