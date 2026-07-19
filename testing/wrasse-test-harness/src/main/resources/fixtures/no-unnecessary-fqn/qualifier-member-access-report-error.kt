package sample

// fixture-aux-file: aux/Aux.kt

val v = sample.aux.Config.value

// expect-error 4:9 no-unnecessary-fqn "Unnecessary fully qualified name"
