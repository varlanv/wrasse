package sample

// fixture-aux-file: aux/Aux.kt

val c = sample.aux.Config

// expect-error 4:9 no-unnecessary-fqn "Unnecessary fully qualified name"
