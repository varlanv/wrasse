package sample.aux

// fixture-aux-file: aux/AuxSamePackageSibling.kt

val w: sample.aux.Widget = TODO()

// expect-error 4:8 no-unnecessary-fqn "Unnecessary fully qualified name"
