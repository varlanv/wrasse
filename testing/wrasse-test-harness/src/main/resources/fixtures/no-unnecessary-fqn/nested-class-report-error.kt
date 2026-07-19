package sample

// fixture-aux-file: aux/Aux.kt

val n: sample.aux.Outer.Nested? = null

// expect-error 4:8 no-unnecessary-fqn "Unnecessary fully qualified name"
